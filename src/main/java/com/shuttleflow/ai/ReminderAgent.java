package com.shuttleflow.ai;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.dto.ReminderRunDto;
import com.shuttleflow.repository.AppointmentRepository;
import com.shuttleflow.repository.AppointmentRepository.DueReminder;
import com.shuttleflow.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Agent 2: runs on a timer, not a prompt. Finds booked appointments starting within 24 hours that
 * have no reminder yet, has the LLM write a personal reminder, and queues it in the M3 outbox,
 * which sends it to the notification service. LLM problems fall back to a template message.
 */
@Service
public class ReminderAgent {
    private static final Logger log = LoggerFactory.getLogger(ReminderAgent.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE MMM d 'at' h:mm a", Locale.US);

    static final int WINDOW_HOURS = 24;
    static final int BATCH_LIMIT = 50;
    static final int MAX_MESSAGE_LENGTH = 500;

    static final String REMINDER_PROMPT = """
            Write a short, friendly reminder (at most 2 sentences, under 300 characters) for a badminton
            appointment. Use the customer's first name, and include the provider name and the start time
            exactly as given. Plain text only, no placeholders.
            """;

    private final ChatClient chatClient;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final SessionAuth sessionAuth;

    public ReminderAgent(ChatClient.Builder chatClientBuilder, AppointmentRepository appointmentRepository,
                         NotificationService notificationService, SessionAuth sessionAuth) {
        this.chatClient = chatClientBuilder.build();
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
        this.sessionAuth = sessionAuth;
    }

    @Scheduled(fixedDelayString = "${shuttleflow.ai.reminder.poll-ms}",
            initialDelayString = "${shuttleflow.ai.reminder.poll-ms}")
    public void scheduledRun() {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Reminder run failed: {}", e.getClass().getName());
        }
    }

    /** POST /ai/reminders/run: lets a provider trigger a run for the demo. */
    public ReminderRunDto runAsProvider(HttpSession session) {
        sessionAuth.requireProvider(session);
        return run();
    }

    /** Synchronized so the timer and a manual run never write the same reminders twice. */
    public synchronized ReminderRunDto run() {
        LocalDateTime now = LocalDateTime.now();
        List<DueReminder> due = appointmentRepository.findDueForReminder(now, now.plusHours(WINDOW_HOURS), BATCH_LIMIT);
        int queued = 0;
        int fallbacks = 0;
        for (DueReminder reminder : due) {
            String message = write(reminder);
            if (message == null) {
                message = NotificationService.reminderTemplate(reminder.serviceName(), reminder.providerName(),
                        reminder.startTime());
                fallbacks++;
            }
            try {
                notificationService.enqueueReminder(reminder.appointmentId(), reminder.customerEmail(), message);
                queued++;
            } catch (DataIntegrityViolationException e) {
                log.info("Reminder already queued appointmentId={}", reminder.appointmentId());
            }
        }
        log.info("Reminder run scanned={} queued={} fallbacks={}", due.size(), queued, fallbacks);
        return new ReminderRunDto(due.size(), queued, fallbacks);
    }

    /** The LLM's reminder, or null when it fails or returns something unusable. */
    String write(DueReminder reminder) {
        String firstName = reminder.customerName().split(" ")[0];
        try {
            String text = chatClient.prompt()
                    .system(REMINDER_PROMPT)
                    .user("Customer first name: " + firstName + "\nService: " + reminder.serviceName()
                            + "\nProvider: " + reminder.providerName()
                            + "\nStart time: " + reminder.startTime().format(WHEN))
                    .call()
                    .content();
            if (text == null || text.isBlank() || text.length() > MAX_MESSAGE_LENGTH) {
                return null;
            }
            return text.trim();
        } catch (RuntimeException e) {
            // Rate limit (429), timeout, bad key: use the template and keep going.
            log.warn("Reminder LLM failed appointmentId={}: {}", reminder.appointmentId(), e.getClass().getName());
            return null;
        }
    }
}
