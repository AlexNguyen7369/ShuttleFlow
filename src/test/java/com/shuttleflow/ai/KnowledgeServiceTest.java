package com.shuttleflow.ai;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AskRequest;
import com.shuttleflow.dto.AskResponseDto;
import com.shuttleflow.service.AiUnavailableException;
import com.shuttleflow.service.InvalidRequestException;
import com.shuttleflow.service.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RAG with an offline keyword embedding model and a stubbed chat model. */
class KnowledgeServiceTest {
    private ChatModel chatModel;
    private KnowledgeService knowledge;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("You can cancel any time before the appointment starts.")))));
        knowledge = new KnowledgeService(new KeywordEmbeddingModel(), ChatClient.builder(chatModel),
                new SessionAuth(), false);
        session = new MockHttpSession();
        session.setAttribute(UserSession.ATTRIBUTE,
                new UserSession(3L, "alex@shuttleflow.com", "Alex Nguyen", "CUSTOMER", null));
    }

    @Test
    void thePolicyFileIsSplitIntoOneDocumentPerSection() {
        assertThat(KnowledgeService.sections()).extracting(d -> d.getMetadata().get("section"))
                .contains("Hours", "Services and prices", "Booking", "Cancellation", "Contact")
                .doesNotContain("ShuttleFlow Policies and FAQ");
    }

    @Test
    void retrievalRanksTheMatchingSectionFirst() {
        List<Document> hits = knowledge.search("Can I cancel my appointment? What is the cancellation policy?");

        assertThat(hits).hasSizeLessThanOrEqualTo(KnowledgeService.TOP_K);
        assertThat(hits.get(0).getMetadata().get("section")).isEqualTo("Cancellation");
    }

    @Test
    void askAnswersFromTheRetrievedContextAndCitesIt() {
        AskResponseDto response = knowledge.ask(new AskRequest("What is the cancellation policy?"), session);

        assertThat(response.getAnswer()).isEqualTo("You can cancel any time before the appointment starts.");
        assertThat(response.getSources()).contains("Cancellation");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
                .contains("using ONLY the policy context")
                .contains("cancel your own booked appointment at any time before it starts")
                .contains("Question: What is the cancellation policy?");
    }

    @Test
    void lookupPolicyToolReturnsTheSectionText() {
        assertThat(knowledge.lookupPolicy("how much is a private coaching session price"))
                .contains("Private Coaching Session | Coach Kim | 60 minutes | 1 | $40.00");
    }

    @Test
    void lookupPolicyReportsAnEmbeddingFailureAsText() {
        EmbeddingModel broken = mock(EmbeddingModel.class);
        when(broken.embed(any(Document.class))).thenThrow(new RuntimeException("429"));
        KnowledgeService failing = new KnowledgeService(broken, ChatClient.builder(chatModel), new SessionAuth(), false);

        assertThat(failing.lookupPolicy("hours?")).isEqualTo("Policy lookup is unavailable right now.");
    }

    @Test
    void badQuestionsAndLoggedOutCallersAreRejected() {
        assertThatThrownBy(() -> knowledge.ask(new AskRequest(" "), session)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> knowledge.ask(new AskRequest("x".repeat(501)), session))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> knowledge.ask(new AskRequest("hours?"), new MockHttpSession()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void modelFailureBecomesAiUnavailable() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("429"));

        assertThatThrownBy(() -> knowledge.ask(new AskRequest("When are you open?"), session))
                .isInstanceOf(AiUnavailableException.class);
    }
}
