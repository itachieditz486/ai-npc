package com.ravex.civilization.llm;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provider abstraction. Implemented: OpenAI-compatible chat completions.
 * To add Anthropic or Ollama later: implement this interface and register it in {@link LlmProviders}.
 */
public interface LlmProvider {
    String id();

    /** True if everything needed (e.g. an API key) is available. */
    boolean isConfigured();

    /** Must never block the caller: returns a future completed on a background thread. Result is the raw JSON text. */
    CompletableFuture<String> chat(String systemPrompt, List<ChatTurn> turns);
}
