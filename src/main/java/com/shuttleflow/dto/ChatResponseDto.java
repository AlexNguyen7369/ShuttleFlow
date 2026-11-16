package com.shuttleflow.dto;

/** The booking assistant's reply to one message. */
public final class ChatResponseDto {
    private final String reply;

    public ChatResponseDto(String reply) {
        this.reply = reply;
    }

    public String getReply() { return reply; }
}
