package com.shuttleflow.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A question about ShuttleFlow policies. */
public final class AskRequest {
    private final String question;

    @JsonCreator
    public AskRequest(@JsonProperty("question") String question) {
        this.question = question;
    }

    public String getQuestion() { return question; }
}
