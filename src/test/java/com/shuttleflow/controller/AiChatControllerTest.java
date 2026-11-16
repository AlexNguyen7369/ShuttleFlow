package com.shuttleflow.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /ai/chat with a stubbed ChatModel: no Gemini call, real auth, validation and wiring. */
@SpringBootTest
@AutoConfigureMockMvc
class AiChatControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ChatModel chatModel;

    @BeforeEach
    void stubModel() {
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Court 3 is open at 9:00.")))));
    }

    @Test
    void customerGetsTheModelReplyAndTheModelSeesTheBookingTools() throws Exception {
        MockHttpSession alex = login("alex@shuttleflow.com", "alex-test");

        chat(alex, "Any courts tomorrow?").andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Court 3 is open at 9:00."));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getText()).isEqualTo("Any courts tomorrow?");
        ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getValue().getOptions();
        assertThat(options.getToolCallbacks()).extracting(t -> t.getToolDefinition().name())
                .contains("findOpenSlots", "listMyAppointments", "proposeBooking", "confirmBooking",
                        "proposeCancel", "confirmCancel", "lookupPolicy");
    }

    @Test
    void followUpMessagesCarryTheConversationAndTheTurnAdvances() throws Exception {
        MockHttpSession alex = login("alex@shuttleflow.com", "alex-test");
        chat(alex, "Book the earliest court this week").andExpect(status().isOk());
        chat(alex, "actually make it 2pm").andExpect(status().isOk());

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(prompts.capture());
        String secondPrompt = prompts.getAllValues().get(1).getContents();
        assertThat(secondPrompt).contains("Book the earliest court this week", "actually make it 2pm");

        assertThat(alex.getAttribute("ai.turn")).isEqualTo(2);
        Object conversationId = alex.getAttribute("ai.conversationId");
        assertThat(conversationId).isInstanceOf(String.class).isNotEqualTo(alex.getId());
    }

    @Test
    void blankOrTooLongMessagesAre400() throws Exception {
        MockHttpSession alex = login("alex@shuttleflow.com", "alex-test");
        chat(alex, "   ").andExpect(status().isBadRequest());
        chat(alex, "x".repeat(1001)).andExpect(status().isBadRequest());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void loggedOutIs401AndProviderIs403() throws Exception {
        chat(new MockHttpSession(), "hi").andExpect(status().isUnauthorized());
        chat(login("court.manager@shuttleflow.com", "court-manager-test"), "hi").andExpect(status().isForbidden());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void modelFailureIs503() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED"));

        chat(login("alex@shuttleflow.com", "alex-test"), "hi").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("The AI assistant is unavailable right now. Please try again later."));
    }

    private ResultActions chat(MockHttpSession session, String message) throws Exception {
        String json = "{\"message\":\"" + message + "\"}";
        return mvc.perform(post("/ai/chat").session(session).contentType("application/json").content(json));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
