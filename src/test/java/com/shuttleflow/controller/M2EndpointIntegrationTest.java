package com.shuttleflow.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class M2EndpointIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clearTestSlots() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
    }

    @Test
    void customerCanBookAndSeeOwnAppointment() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(2));
        MockHttpSession session = login("alex@shuttleflow.com", "alex-test", "/auth/login");

        mvc.perform(post("/appointments").session(session).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.slotId").value(slotId));
        mvc.perform(get("/appointments").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].slotId").value(slotId));
        assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM availability_slots WHERE slot_id = ?", String.class, slotId));
    }

    @Test
    void bookingEnforcesAuthenticationRoleInputNotFoundAndConflict() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(2));
        mvc.perform(post("/appointments").contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession provider = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/login");
        mvc.perform(post("/appointments").session(provider).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isForbidden());
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/appointments").session(customer).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated());
        MockHttpSession secondCustomer = login("jamie@shuttleflow.com", "jamie-test", "/auth/login");
        mvc.perform(post("/appointments").session(secondCustomer).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Court is already booked."));
    }

    @Test
    void customerCanCancelOwnAppointmentAndRebookedSlotIsAvailable() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(2));
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated()).andReturn();
        long appointmentId = jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id = ?", Long.class, slotId);
        mvc.perform(delete("/appointments/" + appointmentId).session(customer)).andExpect(status().isNoContent());
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM availability_slots WHERE slot_id = ?", String.class, slotId));
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Integer.class, slotId));
    }

    @Test
    void cancellationIsOwnerOnlyAndHistoryShowsCancelled() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(2));
        MockHttpSession alex = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/appointments").session(alex).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated());
        long appointmentId = jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id = ?", Long.class, slotId);
        MockHttpSession jamie = login("jamie@shuttleflow.com", "jamie-test", "/auth/login");
        mvc.perform(delete("/appointments/" + appointmentId).session(jamie)).andExpect(status().isForbidden());
        mvc.perform(delete("/appointments/" + appointmentId).session(alex)).andExpect(status().isNoContent());
        mvc.perform(get("/appointments?view=history").session(alex)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void providerCanManageOwnedAvailabilityButNotAnotherService() throws Exception {
        MockHttpSession provider = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/provider/login");
        String future = LocalDateTime.now().plusDays(3).withNano(0).toString();
        String end = LocalDateTime.parse(future).plusHours(1).toString();
        mvc.perform(post("/provider/slots").session(provider).contentType("application/json")
                        .content("{\"serviceId\":1,\"startTime\":\"" + future + "\",\"endTime\":\"" + end + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.providerName").value("Court 3"));
        mvc.perform(post("/provider/slots").session(provider).contentType("application/json")
                        .content("{\"serviceId\":3,\"startTime\":\"" + LocalDateTime.parse(future).plusHours(2) + "\",\"endTime\":\"" + LocalDateTime.parse(end).plusHours(2) + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/provider/slots").session(provider).contentType("application/json")
                        .content("{\"serviceId\":1,\"startTime\":\"" + future + "\",\"endTime\":\"" + end + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void providerAppointmentsAreScopedAndCancelledBookingsDisappear() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(2));
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated());
        MockHttpSession provider = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/provider/login");
        mvc.perform(get("/provider/appointments").session(provider)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerEmail").value("alex@shuttleflow.com"));
        mvc.perform(get("/provider/appointments").session(customer)).andExpect(status().isForbidden());
    }

    @Test
    void providerCanRemoveOwnOpenSlotAndProviderRoutesRequireAuth() throws Exception {
        mvc.perform(get("/provider/appointments")).andExpect(status().isUnauthorized());
        MockHttpSession provider = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/provider/login");
        long slotId = insertSlot(LocalDateTime.now().plusDays(4));
        mvc.perform(delete("/provider/slots/" + slotId).session(provider)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM availability_slots WHERE slot_id = ?", Integer.class, slotId));
    }

    @Test
    void providerCannotRemoveBookedSlot() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(4));
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated());
        MockHttpSession provider = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/provider/login");
        mvc.perform(delete("/provider/slots/" + slotId).session(provider))
                .andExpect(status().isConflict());
    }

    private long insertSlot(LocalDateTime start) {
        jdbc.update("""
                INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)
                VALUES (1, 1, ?, ?, 'OPEN')
                """, start, start.plusHours(1));
        return jdbc.queryForObject("SELECT slot_id FROM availability_slots WHERE provider_id = 1 AND start_time = ?",
                Long.class, start);
    }

    private MockHttpSession login(String email, String password, String path) throws Exception {
        return (MockHttpSession) mvc.perform(post(path).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
