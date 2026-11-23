package com.ravex.civilization.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ravex.civilization.RavexConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Generic OpenAI-compatible /chat/completions client over java.net.http (fully asynchronous).
 * Used for OpenAI, OpenRouter and Ollama (which all speak this protocol).
 */
public class OpenAiProvider implements LlmProvider {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String id;
    private final boolean jsonMode;
    private final Supplier<String> key;
    private final Supplier<String> baseUrl;
    private final Supplier<String> model;

    public OpenAiProvider(String id, boolean jsonMode, Supplier<String> key, Supplier<String> baseUrl, Supplier<String> model) {
        this.id = id;
        this.jsonMode = jsonMode;
        this.key = key;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isConfigured() {
        return !key.get().isEmpty();
    }

    @Override
    public CompletableFuture<String> chat(String systemPrompt, List<ChatTurn> turns) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model.get());
        body.addProperty("max_tokens", RavexConfig.MAX_TOKENS.get());
        body.addProperty("temperature", 0.7);
        if (jsonMode) {
            JsonObject fmt = new JsonObject();
            fmt.addProperty("type", "json_object");
            body.add("response_format", fmt);
        }
        JsonArray msgs = new JsonArray();
        msgs.add(msg("system", systemPrompt));
        for (ChatTurn t : turns) msgs.add(msg(t.role(), t.content()));
        body.add("messages", msgs);

        HttpRequest.Builder rb;
        try {
            rb = HttpRequest.newBuilder(URI.create(baseUrl.get() + "/chat/completions"));
        } catch (IllegalArgumentException ex) {
            return CompletableFuture.failedFuture(new LlmException(0, "bad base URL"));
        }
        rb.timeout(Duration.ofSeconds(RavexConfig.TIMEOUT_SECONDS.get()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key.get())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (id.equals("openrouter")) {
            rb.header("X-Title", "RAVEX AI Civilization");
        }

        return client.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) {
                throw new CompletionException(new LlmException(resp.statusCode(), "HTTP " + resp.statusCode()));
            }
            try {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
            } catch (RuntimeException ex) {
                throw new CompletionException(new LlmException(0, "unreadable response"));
            }
        });
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }
}
