package com.shuttleflow.dto;

import java.util.List;

/** A grounded policy answer and the policy sections it was based on. */
public final class AskResponseDto {
    private final String answer;
    private final List<String> sources;

    public AskResponseDto(String answer, List<String> sources) {
        this.answer = answer;
        this.sources = List.copyOf(sources);
    }

    public String getAnswer() { return answer; }
    public List<String> getSources() { return sources; }
}
