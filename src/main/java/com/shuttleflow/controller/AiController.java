package com.shuttleflow.controller;

import com.shuttleflow.ai.ChatAgentService;
import com.shuttleflow.dto.ChatRequest;
import com.shuttleflow.dto.ChatResponseDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {
    private final ChatAgentService chatAgentService;

    public AiController(ChatAgentService chatAgentService) {
        this.chatAgentService = chatAgentService;
    }

    @PostMapping("/ai/chat")
    public ChatResponseDto chat(@RequestBody ChatRequest request, HttpSession session) {
        return chatAgentService.chat(request, session);
    }
}
