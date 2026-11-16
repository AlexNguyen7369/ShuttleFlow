package com.shuttleflow.controller;

import com.shuttleflow.ai.ChatAgentService;
import com.shuttleflow.ai.KnowledgeService;
import com.shuttleflow.dto.AskRequest;
import com.shuttleflow.dto.AskResponseDto;
import com.shuttleflow.dto.ChatRequest;
import com.shuttleflow.dto.ChatResponseDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {
    private final ChatAgentService chatAgentService;
    private final KnowledgeService knowledgeService;

    public AiController(ChatAgentService chatAgentService, KnowledgeService knowledgeService) {
        this.chatAgentService = chatAgentService;
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/ai/chat")
    public ChatResponseDto chat(@RequestBody ChatRequest request, HttpSession session) {
        return chatAgentService.chat(request, session);
    }

    @PostMapping("/ai/ask")
    public AskResponseDto ask(@RequestBody AskRequest request, HttpSession session) {
        return knowledgeService.ask(request, session);
    }
}
