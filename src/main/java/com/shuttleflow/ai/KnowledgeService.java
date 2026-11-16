package com.shuttleflow.ai;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.dto.AskRequest;
import com.shuttleflow.dto.AskResponseDto;
import com.shuttleflow.service.AiUnavailableException;
import com.shuttleflow.service.InvalidRequestException;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG over the hand-written policy file. Each "## " section becomes one document, embedded with
 * Gemini into an in-memory SimpleVectorStore. Answers use only the retrieved sections.
 */
@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    static final String POLICY_FILE = "knowledge/shuttleflow-policies.md";
    static final int TOP_K = 3;
    static final int MAX_QUESTION_LENGTH = 500;

    static final String ANSWER_PROMPT = """
            You answer questions about ShuttleFlow Arena using ONLY the policy context provided.
            If the context does not contain the answer, say you don't know and suggest emailing
            support@shuttleflow.com. Never invent rules, prices or times. Keep the answer short.
            """;

    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;
    private final SessionAuth sessionAuth;
    private final boolean loadOnStartup;
    private volatile SimpleVectorStore vectorStore;

    public KnowledgeService(EmbeddingModel embeddingModel, ChatClient.Builder chatClientBuilder,
                            SessionAuth sessionAuth,
                            @Value("${shuttleflow.ai.knowledge.load-on-startup:true}") boolean loadOnStartup) {
        this.embeddingModel = embeddingModel;
        this.chatClient = chatClientBuilder.build();
        this.sessionAuth = sessionAuth;
        this.loadOnStartup = loadOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (!loadOnStartup) {
            return;
        }
        try {
            store();
        } catch (RuntimeException e) {
            // No key or Gemini down: the app still runs, and the next question retries the load.
            log.warn("Policy knowledge not loaded at startup: {}", e.getClass().getName());
        }
    }

    /** POST /ai/ask: a grounded answer plus the section titles it came from. */
    public AskResponseDto ask(AskRequest request, HttpSession session) {
        sessionAuth.requireAuthenticated(session);
        String question = request == null ? null : request.getQuestion();
        if (question == null || question.isBlank()) {
            throw new InvalidRequestException("question must not be blank.");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new InvalidRequestException("question must be at most " + MAX_QUESTION_LENGTH + " characters.");
        }
        try {
            List<Document> sections = search(question);
            String answer = chatClient.prompt()
                    .system(ANSWER_PROMPT)
                    .user("Policy context:\n" + context(sections) + "\n\nQuestion: " + question)
                    .call()
                    .content();
            return new AskResponseDto(answer == null ? "" : answer, titles(sections));
        } catch (RuntimeException e) {
            log.warn("Policy answer failed: {}", e.getClass().getName());
            throw new AiUnavailableException();
        }
    }

    @Tool(description = "Look up ShuttleFlow policies (hours, prices, booking and cancellation rules, equipment, "
            + "contact). Returns the most relevant policy sections; answer only from them.")
    public String lookupPolicy(@ToolParam(description = "The policy question") String question) {
        List<Document> sections = search(question);
        return sections.isEmpty() ? "No matching policy found." : context(sections);
    }

    List<Document> search(String question) {
        return store().similaritySearch(SearchRequest.builder().query(question).topK(TOP_K).build());
    }

    private SimpleVectorStore store() {
        SimpleVectorStore current = vectorStore;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (vectorStore == null) {
                SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
                store.add(sections());
                vectorStore = store;
                log.info("Policy knowledge loaded");
            }
            return vectorStore;
        }
    }

    /** One document per "## " section, titled by its heading. */
    static List<Document> sections() {
        String text;
        try {
            text = new ClassPathResource(POLICY_FILE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Document> documents = new ArrayList<>();
        for (String part : text.split("\n## ")) {
            if (part.startsWith("# ")) {
                continue; // the file title and intro
            }
            String title = part.lines().findFirst().orElse("").trim();
            documents.add(new Document("## " + part.trim(), Map.of("section", title)));
        }
        return documents;
    }

    private static String context(List<Document> sections) {
        StringBuilder out = new StringBuilder();
        sections.forEach(d -> out.append(d.getText()).append("\n\n"));
        return out.toString().trim();
    }

    private static List<String> titles(List<Document> sections) {
        return sections.stream().map(d -> String.valueOf(d.getMetadata().get("section"))).toList();
    }
}
