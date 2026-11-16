package com.shuttleflow.ai;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Gemini can split one answer into several parts, and Spring AI returns each part as its own
 * generation; {@code .content()} would keep only the first and cut the reply off. Join them all.
 */
final class AiText {
    private AiText() {
    }

    static String of(ChatResponse response) {
        if (response == null) {
            return "";
        }
        return response.getResults().stream()
                .map(Generation::getOutput)
                .filter(Objects::nonNull)
                .map(output -> output.getText())
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }
}
