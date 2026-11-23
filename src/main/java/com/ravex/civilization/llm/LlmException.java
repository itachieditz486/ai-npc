package com.ravex.civilization.llm;

/** Provider failure. Message never contains request data or keys. */
public class LlmException extends RuntimeException {
    public final int status;

    public LlmException(int status, String message) {
        super(message);
        this.status = status;
    }
}
