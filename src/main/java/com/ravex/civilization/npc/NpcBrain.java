package com.ravex.civilization.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ravex.civilization.RavexCiv;
import com.ravex.civilization.RavexEvents;
import com.ravex.civilization.RavexConfig;
import com.ravex.civilization.llm.ChatTurn;
import com.ravex.civilization.llm.LlmProvider;
import com.ravex.civilization.llm.LlmProviders;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

/** Routes a chat message to the real LLM (async) or to the offline parser, then applies the resulting action. */
public final class NpcBrain {
    private static final Set<String> ACTIONS = Set.of("none", "unsupported", "follow", "stop", "stay", "come", "return_home",
            "set_home", "guard", "protect", "attack_mobs", "stop_attack", "collect_wood", "collect", "deliver", "follow_npc", "help_fight",
            "join_team", "leave_team");

    public static void handle(NpcEntity npc, ServerPlayer player, String text) {
        MinecraftServer server = player.server;
        NpcWorldData data = NpcWorldData.get(server);
        NpcMemory mem = data.memory(npc.npcKey());
        npc.setLang(Lang.detect(text, npc.lang()));
        mem.addTurn("user", player.getGameProfile().getName() + ": " + text);
        data.setDirty();

        LlmProvider provider = RavexConfig.AI_ENABLED.get() ? LlmProviders.current() : null;
        if (provider == null || !provider.isConfigured()) {
            runOffline(npc, player, text, data, "");
            return;
        }

        long now = server.getTickCount();
        long minTicks = RavexConfig.MIN_SECONDS_BETWEEN_REQUESTS.get() * 20L;
        if (npc.isThinking() || now - npc.lastAiTick() < minTicks) {
            npc.say(Lang.t(npc.lang(), "(one moment, I'm still thinking...)", "(un instant, je réfléchis encore...)",
                    "(لحظة، ما زلت أفكر...)"));
            return;
        }
        npc.setThinking(true);
        npc.setLastAiTick(now);

        String system = buildSystemPrompt(npc, player, data);
        List<ChatTurn> turns = new ArrayList<>();
        List<NpcMemory.Turn> hist = mem.history();
        int from = Math.max(0, hist.size() - RavexConfig.HISTORY_TURNS_SENT.get());
        for (int i = from; i < hist.size(); i++) turns.add(new ChatTurn(hist.get(i).role(), hist.get(i).text()));

        provider.chat(system, turns).whenComplete((raw, err) -> server.execute(() -> {
            npc.setThinking(false);
            if (npc.isRemoved()) return;
            if (err != null) {
                Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                LlmProviders.reportFailure(cause);
                runOffline(npc, player, text, data, "");
                return;
            }
            applyAiReply(npc, player, raw, data);
        }));
    }

    private static void applyAiReply(NpcEntity npc, ServerPlayer player, String raw, NpcWorldData data) {
        String reply = raw;
        String action = "none";
        int count = 0;
        String remember = "";
        String arg = "";
        try {
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            if (o.has("reply")) reply = o.get("reply").getAsString();
            if (o.has("action") && o.get("action").isJsonObject()) {
                JsonObject a = o.getAsJsonObject("action");
                if (a.has("type")) action = a.get("type").getAsString().toLowerCase();
                if (a.has("count") && a.get("count").isJsonPrimitive()) count = a.get("count").getAsInt();
                if (a.has("arg") && a.get("arg").isJsonPrimitive()) arg = sanitizeArg(a.get("arg").getAsString());
            }
            if (o.has("remember") && o.get("remember").isJsonPrimitive()) remember = o.get("remember").getAsString();
        } catch (Exception ex) {
            RavexCiv.LOGGER.warn("AI reply was not valid JSON; showing it as plain text.");
        }
        if (!ACTIONS.contains(action)) action = "none";
        NpcMemory mem = data.memory(npc.npcKey());
        mem.addTurn("assistant", reply);
        mem.addFact(remember);
        data.setDirty();
        if (npc.isYassine()) data.writeYassineBackup(player.server);
        npc.say(reply);
        if (!action.equals("none") && !action.equals("unsupported")) execute(npc, player, action, count, arg);
    }

    private static String sanitizeArg(String a) {
        a = a.trim();
        if (a.length() > 32) a = a.substring(0, 32);
        return a.replaceAll("[^\\p{L}\\p{N} _-]", "");
    }

    /** Runs a whitelisted action; if the NPC cannot do it, it says why (never pretends success). */
    private static void execute(NpcEntity npc, ServerPlayer player, String action, int count, String arg) {
        String err = npc.applyAction(action, count, arg, player);
        if (err != null) npc.say(err);
    }

    private static String otherNpcName(NpcEntity self, MinecraftServer server, String text) {
        for (NpcEntity n : RavexEvents.allNpcs(server)) {
            if (n != self && NpcNames.mentions(n.npcName(), text)) return n.npcName();
        }
        return "";
    }

    private static void runOffline(NpcEntity npc, ServerPlayer player, String text, NpcWorldData data, String note) {
        OfflineInterpreter.Result r = OfflineInterpreter.interpret(text, npc.lang());
        String action = r.action();
        String arg = r.arg();
        String reply = r.reply();
        String other = otherNpcName(npc, player.server, text);
        if (action.equals("follow") && !other.isEmpty()) {
            action = "follow_npc";
            arg = other;
            reply = Lang.t(npc.lang(), "Following " + other + ".", "Je suis " + other + ".", "أتبع " + other + ".");
        } else if (action.equals("help_fight")) {
            if (other.isEmpty()) {
                action = "none";
                reply = Lang.t(npc.lang(), "Who should I help? Mention another NPC by name.",
                        "Qui dois-je aider ? Mentionne un autre PNJ par son nom.", "من أساعد؟ اذكر اسم شخصية أخرى.");
            } else {
                arg = other;
            }
        }
        String line = "[offline] " + note + reply;
        data.memory(npc.npcKey()).addTurn("assistant", line);
        data.setDirty();
        if (npc.isYassine()) data.writeYassineBackup(player.server);
        npc.say(line);
        if (!action.equals("none")) execute(npc, player, action, r.count(), arg);
    }

    private static String buildSystemPrompt(NpcEntity npc, ServerPlayer player, NpcWorldData data) {
        NpcProfile p = data.profile(npc.npcKey());
        String personality = p != null ? p.personality() : "A friendly survivor.";
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(npc.npcName()).append(", an NPC living in a Minecraft Java 1.21.1 world, chatting with the player \"")
                .append(player.getGameProfile().getName()).append("\" in text chat.\n");
        sb.append("Personality: ").append(personality).append("\n");
        sb.append("Reply in the language the player used (Arabic, English or French), in character, at most 2 short sentences. ")
                .append("Never claim to have done something you have not actually done.\n");
        sb.append(String.format("Current state: health %.0f/%.0f, position %d %d %d, mission %s (progress %d/%d), inventory: %s.\n",
                npc.getHealth(), npc.getMaxHealth(), npc.getBlockX(), npc.getBlockY(), npc.getBlockZ(),
                npc.mission().name().toLowerCase(), npc.missionProgress(), npc.missionTarget(), npc.inventorySummary()));
        List<String> facts = data.memory(npc.npcKey()).facts();
        if (!facts.isEmpty()) {
            sb.append("Things you remember:\n");
            for (String f : facts) sb.append("- ").append(f).append("\n");
        }
        sb.append("Abilities that are ACTUALLY implemented right now (only these action types): ")
                .append("follow (walk after the player), stop, stay (hold position), come (walk to the player), return_home (go home and store items in a chest), ")
                .append("set_home (home = where the player stands), guard (hold this area and fight monsters near it), protect (stay near the player and fight monsters), ")
                .append("attack_mobs (hunt hostile mobs; arg = zombie|skeleton|creeper|spider|enderman|witch|slime, empty for any, or \"look\" for the mob the player looks at; count 1 = attack just one), stop_attack, ")
                .append("collect_wood (count; arg = oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry or empty; you walk to trees, chop them and bring the logs to the player), ")
                .append("collect (arg = stone|coal|iron|gold|diamond|ores|caving, count; needs a suitable pickaxe; you only mine what you can see, explore real caves, then bring everything to the player), ")
                .append("deliver (give the player what you are carrying), ")
                .append("follow_npc (arg = other NPC name), help_fight (arg = other NPC name), join_team, leave_team. ")
                .append("Anything else (building, crafting, farming, smelting, Nether, End, etc.) is NOT implemented yet: ")
                .append("use action type \"unsupported\" and honestly say you cannot do it yet.\n");
        sb.append("Respond ONLY with JSON: {\"reply\":\"...\",\"action\":{\"type\":\"none\",\"count\":0,\"arg\":\"\"},")
                .append("\"remember\":\"optional short lasting fact worth remembering, else empty\"}");
        return sb.toString();
    }

    private NpcBrain() {}
}
