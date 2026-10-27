package com.shuttleflow.controller;

import com.shuttleflow.service.MockNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MockNotificationControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private MockNotificationService mockNotificationService;

    private static String body(String key, String recipient) {
        return "{\"recipient\":\"" + recipient + "\",\"type\":\"BOOKING_CONFIRMED\","
                + "\"message\":\"Your court is booked.\",\"idempotencyKey\":\"" + key + "\",\"appointmentId\":1}";
    }

    @Test
    void newKeyIsAcceptedAndRepeatKeyIsStoredOnce() throws Exception {
        int before = mockNotificationService.deliveredCount();
        mvc.perform(post("/mock-notifications").contentType("application/json").content(body("TEST:dup", "a@b.com")))
                .andExpect(status().isAccepted());
        mvc.perform(post("/mock-notifications").contentType("application/json").content(body("TEST:dup", "a@b.com")))
                .andExpect(status().isOk());
        assertEquals(before + 1, mockNotificationService.deliveredCount());
    }

    @Test
    void missingRecipientIsRejected() throws Exception {
        mvc.perform(post("/mock-notifications").contentType("application/json").content(body("TEST:blank", "")))
                .andExpect(status().isBadRequest());
    }
}
