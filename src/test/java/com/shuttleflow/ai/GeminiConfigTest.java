package com.shuttleflow.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GeminiConfigTest {
    @Autowired private ChatModel chatModel;
    @Autowired private EmbeddingModel embeddingModel;
    @Autowired private Environment env;

    @Test
    void geminiBeansStartWithADummyKeyAndNoNetworkCall() {
        assertThat(chatModel).isNotNull();
        assertThat(embeddingModel).isNotNull();
    }

    @Test
    void vertexPropertiesStayUnsetSoTheFreeKeyWorks() {
        assertThat(env.getProperty("spring.ai.google.genai.project-id")).isNull();
        assertThat(env.getProperty("spring.ai.google.genai.location")).isNull();
    }
}
