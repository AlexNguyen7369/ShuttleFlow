package com.shuttleflow.ai;

import com.shuttleflow.auth.JwtService;
import com.shuttleflow.dto.ReminderRunDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Agent 2 against the real outbox, with a stubbed chat model. */
@SpringBootTest
@AutoConfigureMockMvc
class ReminderAgentTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReminderAgent reminderAgent;
    @Autowired private MockMvc mvc;
    @MockitoBean private ChatModel chatModel;
    private long dueAppointmentId;
    private LocalDateTime dueStart;
    private String goodReply;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        dueStart = now.plusHours(3);
        dueAppointmentId = appointment(dueStart, 3L, "BOOKED");          // due: Alex, in 3 hours
        appointment(now.plusDays(2), 4L, "BOOKED");                       // not due: 2 days away
        appointment(now.plusHours(5), 4L, "CANCELLED");                   // not due: cancelled
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        goodReply = "Hi Alex! Your Singles Court Rental at Court 3 starts at "
                + dueStart.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)) + ". See you there!";
        reply(goodReply);
    }

    @Test
    void queuesOneLlmReminderPerDueAppointmentAndNoneOnReRun() {
        ReminderRunDto first = reminderAgent.run();

        assertThat(first.getScanned()).isEqualTo(1);
        assertThat(first.getQueued()).isEqualTo(1);
        assertThat(first.getFallbacks()).isZero();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT appointment_id, idempotency_key, recipient, message FROM notifications WHERE type = 'APPOINTMENT_REMINDER'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("IDEMPOTENCY_KEY", "REMINDER:" + dueAppointmentId)
                .containsEntry("RECIPIENT", "alex@shuttleflow.com")
                .containsEntry("MESSAGE", goodReply);

        ReminderRunDto second = reminderAgent.run();
        assertThat(second.getScanned()).isZero();
        assertThat(second.getQueued()).isZero();
        assertThat(reminderCount()).isEqualTo(1);
    }

    @Test
    void llmFailureFallsBackToTheTemplateAndStillQueues() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED"));

        ReminderRunDto run = reminderAgent.run();

        assertThat(run.getQueued()).isEqualTo(1);
        assertThat(run.getFallbacks()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE type = 'APPOINTMENT_REMINDER'", String.class))
                .startsWith("Reminder: your Singles Court Rental at Court 3 starts ");
    }

    @Test
    void selfCheckRejectsAWrongTimeAndUsesTheTemplate() throws Exception {
        reply("Hi Alex! Your Singles Court Rental at Court 3 starts at "
                + dueStart.plusHours(2).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)) + ".");
        long fallbacksBefore = metric("reminderFallbacks");

        ReminderRunDto run = reminderAgent.run();

        assertThat(run.getQueued()).isEqualTo(1);
        assertThat(run.getFallbacks()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE type = 'APPOINTMENT_REMINDER'", String.class))
                .startsWith("Reminder: your Singles Court Rental at Court 3 starts ");
        assertThat(metric("reminderFallbacks")).isEqualTo(fallbacksBefore + 1);
    }

    @Test
    void selfCheckRejectsTheWrongCourt() {
        reply(goodReply.replace("Court 3", "Court 7"));

        assertThat(reminderAgent.run().getFallbacks()).isEqualTo(1);
    }

    @Test
    void queuedRemindersAreCountedInMetrics() throws Exception {
        long before = metric("remindersQueued");
        reminderAgent.run();
        assertThat(metric("remindersQueued")).isEqualTo(before + 1);
    }

    @Test
    void manualTriggerIsProviderOnly() throws Exception {
        mvc.perform(post("/ai/reminders/run").cookie(login("alex@shuttleflow.com", "alex-test")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ai/reminders/run")).andExpect(status().isUnauthorized());

        mvc.perform(post("/ai/reminders/run").cookie(login("court.manager@shuttleflow.com", "court-manager-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(1))
                .andExpect(jsonPath("$.queued").value(1))
                .andExpect(jsonPath("$.fallbacks").value(0));
        assertThat(reminderCount()).isEqualTo(1);
    }

    private long metric(String name) throws Exception {
        String json = mvc.perform(get("/metrics")).andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"" + name + "\":(\\d+).*", "$1"));
    }

    private void reply(String text) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    private int reminderCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'APPOINTMENT_REMINDER'", Integer.class);
    }

    private long appointment(LocalDateTime start, long userId, String status) {
        boolean booked = "BOOKED".equals(status);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status) VALUES (1, 1, ?, ?, ?)",
                start, start.plusHours(1), booked ? "BOOKED" : "OPEN");
        long slotId = jdbc.queryForObject("SELECT slot_id FROM availability_slots WHERE provider_id = 1 AND start_time = ?",
                Long.class, start);
        jdbc.update("INSERT INTO appointments (slot_id, user_id, status, active_slot_id) VALUES (?, ?, ?, ?)",
                slotId, userId, status, booked ? slotId : null);
        return jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id = ?", Long.class, slotId);
    }

    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(JwtService.COOKIE_NAME);
    }
}
