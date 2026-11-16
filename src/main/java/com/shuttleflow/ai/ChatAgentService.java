package com.shuttleflow.ai;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.dto.ChatRequest;
import com.shuttleflow.dto.ChatResponseDto;
import com.shuttleflow.service.AiUnavailableException;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.InvalidRequestException;
import com.shuttleflow.service.SlotService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Agent 1: the booking assistant. Each request builds fresh BookingTools holding the caller's
 * session and turn number, so the model acts only as that user and confirm-before-write is
 * enforced in code. Conversation memory is keyed by a random id kept in the session, never the
 * session id itself.
 */
@Service
public class ChatAgentService {
    private static final Logger log = LoggerFactory.getLogger(ChatAgentService.class);

    static final int MAX_MESSAGE_LENGTH = 1000;
    static final String TURN_KEY = "ai.turn";
    static final String CONVERSATION_KEY = "ai.conversationId";

    static final String SYSTEM_PROMPT = """
            You are the ShuttleFlow booking assistant for a badminton facility with courts and coaches.
            You act for the logged-in customer and can only use the provided tools.

            Rules:
            - Never invent slots, appointments, ids, prices or times. Get them from the tools.
            - To book: call findOpenSlots, pick the slot the user asked for, call proposeBooking, then show the
              court or coach, date, time and price and ask the user to confirm. Call confirmBooking only after
              the user says yes in a new message. Never propose and confirm in the same reply.
            - To cancel: call listMyAppointments, call proposeCancel, ask the user to confirm, and call
              confirmCancel only after they say yes in a new message.
            - If a tool says "Court is already booked." or that a slot is taken, tell the user, then call
              findOpenSlots and offer the nearest open alternatives.
            - For questions about hours, prices, rules, equipment or contact, call lookupPolicy and answer only
              from what it returns.
            - If a tool returns an error, explain it plainly. Do not retry the same action silently.
            - "This week" means today through the next 6 days. Use dates in YYYY-MM-DD for tools.
            - Keep replies short and friendly. Show times like "Tue Nov 17, 9:00-10:00 AM".
            """;

    private final ChatClient chatClient;
    private final SlotService slotService;
    private final AppointmentService appointmentService;
    private final SessionAuth sessionAuth;
    private final KnowledgeService knowledgeService;

    public ChatAgentService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, SlotService slotService,
                            AppointmentService appointmentService, SessionAuth sessionAuth,
                            KnowledgeService knowledgeService) {
        // shortcut: in-memory chat memory is lost on restart and never evicts old conversations; switch to
        // JDBC chat memory if conversations must survive restarts or the app runs for long.
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.slotService = slotService;
        this.appointmentService = appointmentService;
        this.sessionAuth = sessionAuth;
        this.knowledgeService = knowledgeService;
    }

    public ChatResponseDto chat(ChatRequest request, HttpSession session) {
        sessionAuth.requireCustomer(session);
        String message = request == null ? null : request.getMessage();
        if (message == null || message.isBlank()) {
            throw new InvalidRequestException("message must not be blank.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new InvalidRequestException("message must be at most " + MAX_MESSAGE_LENGTH + " characters.");
        }
        int turn = nextTurn(session);
        String conversationId = conversationId(session);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"));
        try {
            String reply = chatClient.prompt()
                    .system(SYSTEM_PROMPT + "\nCurrent local date and time: " + now)
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .tools(new BookingTools(slotService, appointmentService, session, turn), knowledgeService)
                    .call()
                    .content();
            return new ChatResponseDto(reply == null ? "" : reply);
        } catch (RuntimeException e) {
            // Class name only: the message could echo the prompt or the key.
            log.warn("AI chat failed: {}", e.getClass().getName());
            throw new AiUnavailableException();
        }
    }

    /** Atomic per session, so two concurrent messages can never share a turn number. */
    private static int nextTurn(HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            int turn = session.getAttribute(TURN_KEY) instanceof Integer previous ? previous + 1 : 1;
            session.setAttribute(TURN_KEY, turn);
            return turn;
        }
    }

    private static String conversationId(HttpSession session) {
        if (session.getAttribute(CONVERSATION_KEY) instanceof String existing) {
            return existing;
        }
        String id = UUID.randomUUID().toString();
        session.setAttribute(CONVERSATION_KEY, id);
        return id;
    }
}
