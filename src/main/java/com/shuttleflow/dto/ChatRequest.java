package com.shuttleflow.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One user message to the booking assistant. */
public final class ChatRequest {
    private final String message;

    @JsonCreator
    public ChatRequest(@JsonProperty("message") String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
}
