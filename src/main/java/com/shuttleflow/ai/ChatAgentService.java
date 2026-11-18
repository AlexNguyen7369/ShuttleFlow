package com.shuttleflow.ai;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.ChatRequest;
import com.shuttleflow.dto.ChatResponseDto;
import com.shuttleflow.repository.AiConversationRepository.TurnRecord;
import com.shuttleflow.service.AiConversationService;
import com.shuttleflow.service.AiUnavailableException;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.InvalidRequestException;
import com.shuttleflow.service.SlotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Agent 1: the booking assistant. Each request builds fresh BookingTools holding the caller's
 * identity and turn number, so the model acts only as that user and confirm-before-write is
 * enforced in code. The turn number and the random conversation (memory) id live in the
 * ai_conversations table, keyed by user.
 */
@Service
public class ChatAgentService {
    private static final Logger log = LoggerFactory.getLogger(ChatAgentService.class);

    static final int MAX_MESSAGE_LENGTH = 1000;

    static final String SYSTEM_PROMPT = """
            You are the ShuttleFlow booking assistant for a badminton facility with courts and coaches.
            You act for the logged-in customer and can only use the provided tools.

            Rules:
            - Never invent slots, appointments, ids, prices or times. Get them from the tools.
            - To book: call findOpenSlots, pick the slot the user asked for, and call proposeBooking for it in
              that same reply. Then show the court or coach, date, time and price and ask the user to confirm.
              Never ask the user to confirm a booking you have not just proposed with proposeBooking; if the
              user changes the time or day, call findOpenSlots and proposeBooking again for the new slot.
            - When the user says yes to your proposal, call confirmBooking right away. Never propose and
              confirm in the same reply.
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
    private final AiConversationService conversations;

    public ChatAgentService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, SlotService slotService,
                            AppointmentService appointmentService, SessionAuth sessionAuth,
                            KnowledgeService knowledgeService, AiConversationService conversations) {
        // shortcut: in-memory chat memory is lost on restart and never evicts old conversations; switch to
        // JDBC chat memory if conversations must survive restarts or the app runs for long.
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.slotService = slotService;
        this.appointmentService = appointmentService;
        this.sessionAuth = sessionAuth;
        this.knowledgeService = knowledgeService;
        this.conversations = conversations;
    }

    public ChatResponseDto chat(ChatRequest request, UserSession caller) {
        UserSession user = sessionAuth.requireCustomer(caller);
        String message = request == null ? null : request.getMessage();
        if (message == null || message.isBlank()) {
            throw new InvalidRequestException("message must not be blank.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new InvalidRequestException("message must be at most " + MAX_MESSAGE_LENGTH + " characters.");
        }
        TurnRecord turn = conversations.nextTurn(user.getUserId());
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"));
        try {
            String reply = AiText.of(chatClient.prompt()
                    .system(SYSTEM_PROMPT + "\nCurrent local date and time: " + now)
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, turn.conversationId()))
                    .tools(new BookingTools(slotService, appointmentService, conversations, caller,
                            user.getUserId(), turn.turn()), knowledgeService)
                    .call()
                    .chatResponse());
            return new ChatResponseDto(reply);
        } catch (RuntimeException e) {
            // Class name only: the message could echo the prompt or the key.
            log.warn("AI chat failed: {}", e.getClass().getName());
            throw new AiUnavailableException();
        }
    }

}
