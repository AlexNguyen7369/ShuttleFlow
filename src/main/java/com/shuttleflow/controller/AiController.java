package com.shuttleflow.controller;

import com.shuttleflow.auth.JwtAuthFilter;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.ai.ChatAgentService;
import com.shuttleflow.ai.KnowledgeService;
import com.shuttleflow.ai.ReminderAgent;
import com.shuttleflow.dto.AskRequest;
import com.shuttleflow.dto.AskResponseDto;
import com.shuttleflow.dto.ChatRequest;
import com.shuttleflow.dto.ChatResponseDto;
import com.shuttleflow.dto.ReminderRunDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {
    private final ChatAgentService chatAgentService;
    private final KnowledgeService knowledgeService;
    private final ReminderAgent reminderAgent;

    public AiController(ChatAgentService chatAgentService, KnowledgeService knowledgeService,
                        ReminderAgent reminderAgent) {
        this.chatAgentService = chatAgentService;
        this.knowledgeService = knowledgeService;
        this.reminderAgent = reminderAgent;
    }

    @PostMapping("/ai/chat")
    public ChatResponseDto chat(@RequestBody ChatRequest request, @RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return chatAgentService.chat(request, caller);
    }

    @PostMapping("/ai/ask")
    public AskResponseDto ask(@RequestBody AskRequest request, @RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return knowledgeService.ask(request, caller);
    }

    @PostMapping("/ai/reminders/run")
    public ReminderRunDto runReminders(@RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return reminderAgent.runAsProvider(caller);
    }
}
