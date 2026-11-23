package com.ravex.civilization.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Validates a key/connection WITHOUT sending any conversation and without ever returning or logging the key.
 * OpenRouter: GET /auth/key (free). OpenAI / Ollama: GET /models.
 */
public final class ConnectionTester {
    public record Result(boolean ok, String message) {}

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public static CompletableFuture<Result> test(String provider, String key, String baseUrl) {
        boolean needsKey = !provider.equals("ollama");
        if (needsKey && (key == null || key.isBlank())) {
            return CompletableFuture.completedFuture(new Result(false, "No API key set."));
        }
        String path = provider.equals("openrouter") || provider.equals("auto") ? "/auth/key" : "/models";
        HttpRequest.Builder rb;
        try {
            rb = HttpRequest.newBuilder(URI.create(baseUrl + path));
        } catch (IllegalArgumentException ex) {
            return CompletableFuture.completedFuture(new Result(false, "The base URL in the config is invalid."));
        }
        rb.timeout(Duration.ofSeconds(10)).GET();
        if (needsKey) rb.header("Authorization", "Bearer " + key.trim());
        return CLIENT.sendAsync(rb.build(), HttpResponse.BodyHandlers.discarding())
                .thenApply(resp -> {
                    int s = resp.statusCode();
                    if (s == 200) return new Result(true, "Connected - the key was accepted.");
                    if (s == 401 || s == 403) return new Result(false, "The key was rejected (HTTP " + s + ").");
                    if (s == 402) return new Result(false, "The account has no credits (HTTP 402).");
                    if (s == 429) return new Result(false, "Rate limited (HTTP 429). Try again shortly.");
                    return new Result(false, "The provider answered HTTP " + s + ".");
                })
                .exceptionally(ex -> new Result(false, "No connection - NPCs will use offline mode."));
    }

    private ConnectionTester() {}
}
