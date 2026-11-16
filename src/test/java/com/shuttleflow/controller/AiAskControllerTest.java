package com.shuttleflow.controller;

import com.shuttleflow.ai.KeywordEmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /ai/ask end to end with offline embeddings and a stubbed chat model. */
@SpringBootTest
@AutoConfigureMockMvc
class AiAskControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ChatModel chatModel;
    // Real default methods, so SimpleVectorStore's batching reaches the stubbed call() below.
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private EmbeddingModel embeddingModel;

    @BeforeEach
    void stubModels() {
        KeywordEmbeddingModel keywords = new KeywordEmbeddingModel();
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(i -> keywords.call(i.getArgument(0)));
        when(embeddingModel.embed(any(Document.class))).thenAnswer(i -> keywords.embed((Document) i.getArgument(0)));
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("ShuttleFlow Arena is open 7:00 AM to 10:00 PM.")))));
    }

    @Test
    void loggedInUserGetsAGroundedAnswerWithSources() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"alex-test\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mvc.perform(post("/ai/ask").session(session).contentType("application/json")
                        .content("{\"question\":\"What are the opening hours?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("ShuttleFlow Arena is open 7:00 AM to 10:00 PM."))
                .andExpect(jsonPath("$.sources[0]").value("Hours"));
    }

    @Test
    void loggedOutIs401() throws Exception {
        mvc.perform(post("/ai/ask").contentType("application/json").content("{\"question\":\"hours?\"}"))
                .andExpect(status().isUnauthorized());
    }
}
