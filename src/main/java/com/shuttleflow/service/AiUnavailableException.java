package com.shuttleflow.service;

/** The AI model could not answer (no or bad key, rate limit, timeout); mapped to 503. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() {
        super("The AI assistant is unavailable right now. Please try again later.");
    }
}
