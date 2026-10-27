package com.shuttleflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MetricsControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void bookingAndConflictAreCounted() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(4);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)"
                + " VALUES (1, 1, ?, ?, 'OPEN')", start, start.plusHours(1));
        long slotId = jdbc.queryForObject("SELECT slot_id FROM availability_slots WHERE provider_id = 1 AND start_time = ?",
                Long.class, start);
        JsonNode before = metrics();

        String booking = "{\"slotId\":" + slotId + "}";
        mvc.perform(post("/appointments").session(login("alex@shuttleflow.com", "alex-test"))
                .contentType("application/json").content(booking)).andExpect(status().isCreated());
        mvc.perform(post("/appointments").session(login("jamie@shuttleflow.com", "jamie-test"))
                .contentType("application/json").content(booking)).andExpect(status().isConflict());

        JsonNode after = metrics();
        assertEquals(before.get("bookingsCreated").asLong() + 1, after.get("bookingsCreated").asLong());
        assertEquals(before.get("bookingConflicts").asLong() + 1, after.get("bookingConflicts").asLong());
    }

    private JsonNode metrics() throws Exception {
        return json.readTree(mvc.perform(get("/metrics")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
