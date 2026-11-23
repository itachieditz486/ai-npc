package com.ravex.civilization.client;

import com.ravex.civilization.RavexConfig;
import com.ravex.civilization.llm.ApiKeys;
import com.ravex.civilization.llm.ConnectionTester;
import com.ravex.civilization.llm.LlmProviders;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.util.List;

/**
 * In-game settings (Mods > RAVEX AI Civilization > Config). The API key is typed into a masked field, saved only to a
 * local file in the Minecraft config folder, never shown again, never logged, never sent anywhere except the provider.
 */
public class RavexConfigScreen extends Screen {
    private final Screen parent;
    private String provider;
    private boolean aiEnabled;
    private boolean showKey;
    private Component status = Component.literal("Not tested yet.");
    private int statusColor = 0xAAAAAA;

    private EditBox keyBox;
    private EditBox modelBox;
    private Button showButton;

    public RavexConfigScreen(Screen parent) {
        super(Component.literal("RAVEX AI Civilization - AI Settings"));
        this.parent = parent;
        this.provider = RavexConfig.PROVIDER.get().toLowerCase();
        if (!List.of("auto", "openrouter", "openai", "ollama").contains(provider)) provider = "auto";
        this.aiEnabled = RavexConfig.AI_ENABLED.get();
    }

    private String keyProvider() {
        return provider.equals("openai") ? "openai" : "openrouter";
    }

    private String modelFor(String p) {
        return switch (p) {
            case "openai" -> RavexConfig.MODEL.get();
            case "ollama" -> RavexConfig.OLLAMA_MODEL.get();
            default -> RavexConfig.OPENROUTER_MODEL.get();
        };
    }

    private String baseUrlFor(String p) {
        return switch (p) {
            case "openai" -> RavexConfig.BASE_URL.get();
            case "ollama" -> RavexConfig.OLLAMA_BASE_URL.get();
            default -> RavexConfig.OPENROUTER_BASE_URL.get();
        };
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 36;

        addRenderableWidget(CycleButton.onOffBuilder(aiEnabled)
                .create(cx - 150, y, 300, 20, Component.literal("Cloud / local AI"), (b, v) -> aiEnabled = v));
        y += 26;
        addRenderableWidget(CycleButton.<String>builder(s -> Component.literal(s))
                .withValues(List.of("auto", "openrouter", "openai", "ollama"))
                .withInitialValue(provider)
                .create(cx - 150, y, 300, 20, Component.literal("AI Provider"), (b, v) -> {
                    provider = v;
                    modelBox.setValue(modelFor(provider));
                    keyBox.setEditable(!provider.equals("ollama"));
                }));
        y += 30;

        keyBox = new EditBox(this.font, cx - 150, y, 240, 20, Component.literal("API key"));
        keyBox.setMaxLength(300);
        keyBox.setHint(Component.literal("Paste API key here (hidden)"));
        applyKeyMask();
        keyBox.setEditable(!provider.equals("ollama"));
        addRenderableWidget(keyBox);
        showButton = addRenderableWidget(Button.builder(Component.literal("Show"), b -> {
            showKey = !showKey;
            b.setMessage(Component.literal(showKey ? "Hide" : "Show"));
            applyKeyMask();
        }).bounds(cx + 95, y, 55, 20).build());
        y += 28;

        modelBox = new EditBox(this.font, cx - 150, y, 300, 20, Component.literal("Model ID"));
        modelBox.setMaxLength(120);
        modelBox.setValue(modelFor(provider));
        addRenderableWidget(modelBox);
        y += 30;

        addRenderableWidget(Button.builder(Component.literal("Test connection"), b -> testConnection())
                .bounds(cx - 150, y, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove saved key"), b -> removeKey())
                .bounds(cx + 5, y, 145, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(cx - 150, y, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply & close"), b -> {
            if (save()) {
                LlmProviders.resetPause();
                onClose();
            }
        }).bounds(cx - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx + 55, y, 95, 20).build());
    }

    private void applyKeyMask() {
        if (showKey) {
            keyBox.setFormatter((text, offset) -> FormattedCharSequence.forward(text, Style.EMPTY));
        } else {
            keyBox.setFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        }
    }

    private void setStatus(String msg, boolean ok) {
        status = Component.literal(msg);
        statusColor = ok ? 0x55FF55 : 0xFF5555;
    }

    private void testConnection() {
        String typed = keyBox.getValue().trim();
        String key = typed.isEmpty() ? (keyProvider().equals("openai") ? ApiKeys.openAi() : ApiKeys.openRouter()) : typed;
        status = Component.literal("Testing...");
        statusColor = 0xFFFF55;
        ConnectionTester.test(provider, key, baseUrlFor(provider)).thenAccept(r ->
                this.minecraft.execute(() -> setStatus(r.message(), r.ok())));
    }

    private void removeKey() {
        try {
            ApiKeys.clear(keyProvider());
            keyBox.setValue("");
            setStatus("Saved key removed. (An environment variable, if set, still applies.)", true);
        } catch (IOException ex) {
            setStatus("Could not remove the key file.", false);
        }
    }

    /** Persists settings. Returns false on failure. */
    private boolean save() {
        try {
            RavexConfig.AI_ENABLED.set(aiEnabled);
            RavexConfig.AI_ENABLED.save();
            RavexConfig.PROVIDER.set(provider);
            RavexConfig.PROVIDER.save();
            String model = modelBox.getValue().trim();
            if (!model.isEmpty()) {
                var cv = switch (provider) {
                    case "openai" -> RavexConfig.MODEL;
                    case "ollama" -> RavexConfig.OLLAMA_MODEL;
                    default -> RavexConfig.OPENROUTER_MODEL;
                };
                cv.set(model);
                cv.save();
            }
            String typed = keyBox.getValue().trim();
            if (!typed.isEmpty() && !provider.equals("ollama")) {
                ApiKeys.save(keyProvider(), typed);
                keyBox.setValue(""); // do not keep the key in the UI after saving
            }
            setStatus("Saved.", true);
            return true;
        } catch (IOException | RuntimeException ex) {
            setStatus("Could not save settings.", false);
            return false;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 14, 0xFFFFFF);
        String src = provider.equals("ollama") ? "not needed (local)" : ApiKeys.source(keyProvider());
        g.drawString(this.font, "Saved key: " + src, cx - 150, this.height - 92, 0xA0A0A0, false);
        String mode = LlmProviders.statusText();
        g.drawString(this.font, "Mode: " + mode, cx - 150, this.height - 80, mode.startsWith("ONLINE") ? 0x55FF55 : 0xFFAA00, false);
        g.drawString(this.font, status, cx - 150, this.height - 66, statusColor, false);
        g.drawString(this.font, "Offline mode (built-in commands) is used automatically when no key or internet is available.",
                cx - 150, this.height - 50, 0x808080, false);
        g.drawString(this.font, "The key is stored only in your Minecraft config folder. 'Test' sends no chat data.",
                cx - 150, this.height - 40, 0x808080, false);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
