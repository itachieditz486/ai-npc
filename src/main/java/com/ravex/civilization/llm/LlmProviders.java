package com.ravex.civilization.llm;

import com.ravex.civilization.RavexCiv;
import com.ravex.civilization.RavexConfig;

import java.util.concurrent.CompletionException;

/**
 * Chooses the cloud/local provider, or null (= offline mode). Switching is automatic:
 * after a failure the provider is paused for a while and NPCs quietly use the offline system,
 * then the provider is tried again.
 */
public final class LlmProviders {
    private static final OpenAiProvider OPENROUTER = new OpenAiProvider("openrouter", false,
            ApiKeys::openRouter, RavexConfig.OPENROUTER_BASE_URL::get, RavexConfig.OPENROUTER_MODEL::get);
    private static final OpenAiProvider OPENAI = new OpenAiProvider("openai", true,
            ApiKeys::openAi, RavexConfig.BASE_URL::get, RavexConfig.MODEL::get);
    private static final OpenAiProvider OLLAMA = new OpenAiProvider("ollama", true,
            () -> "ollama", RavexConfig.OLLAMA_BASE_URL::get, RavexConfig.OLLAMA_MODEL::get);

    private static volatile long disabledUntilMs = 0;

    /** Returns a usable provider, or null if offline mode should be used right now. */
    public static LlmProvider current() {
        if (System.currentTimeMillis() < disabledUntilMs) return null;
        String id = RavexConfig.PROVIDER.get().toLowerCase();
        switch (id) {
            case "openrouter":
                return OPENROUTER.isConfigured() ? OPENROUTER : null;
            case "openai":
                return OPENAI.isConfigured() ? OPENAI : null;
            case "ollama":
                return OLLAMA;
            case "auto":
                if (OPENROUTER.isConfigured()) return OPENROUTER;
                if (OPENAI.isConfigured()) return OPENAI;
                return null;
            default:
                return null; // unknown / unimplemented provider (e.g. anthropic): offline
        }
    }

    /** Lets the settings screen retry immediately after the user changed something. */
    public static void resetPause() {
        disabledUntilMs = 0;
    }

    /** One-line human readable mode, for the settings screen. Never contains a key. */
    public static String statusText() {
        if (!RavexConfig.AI_ENABLED.get()) return "OFFLINE - cloud AI is switched off in settings";
        long left = disabledUntilMs - System.currentTimeMillis();
        if (left > 0) return "OFFLINE for now - provider paused after an error (" + (left / 1000) + " s), retrying automatically";
        LlmProvider p = current();
        if (p == null) return "OFFLINE - no API key / provider set up (built-in commands only)";
        return "ONLINE - using " + p.id();
    }

    /** Called when a request failed. Pauses the provider and logs ONE line without any secrets. */
    public static void reportFailure(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        long pauseMs;
        String why;
        if (t instanceof LlmException le) {
            int s = le.status;
            if (s == 401 || s == 403) { pauseMs = 600_000; why = "the API key was rejected (HTTP " + s + ")"; }
            else if (s == 402) { pauseMs = 600_000; why = "the account has no credits (HTTP 402)"; }
            else if (s == 404) { pauseMs = 300_000; why = "model or endpoint not available (HTTP 404)"; }
            else if (s == 429) { pauseMs = 60_000; why = "rate limited (HTTP 429)"; }
            else if (s >= 500) { pauseMs = 30_000; why = "provider error (HTTP " + s + ")"; }
            else { pauseMs = 30_000; why = le.getMessage(); }
        } else {
            pauseMs = 30_000;
            why = "no connection to the provider";
        }
        disabledUntilMs = System.currentTimeMillis() + pauseMs;
        RavexCiv.LOGGER.warn("AI provider unavailable ({}). Using offline mode for {} s.", why, pauseMs / 1000);
    }

    private LlmProviders() {}
}
