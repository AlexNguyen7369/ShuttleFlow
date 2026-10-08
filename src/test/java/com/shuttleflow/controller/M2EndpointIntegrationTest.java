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

    @Test
    void pastSlotBookingIsConflictAndMismatchedServiceIsBadRequest() throws Exception {
        long pastSlot = insertSlot(LocalDateTime.now().minusHours(2).withNano(0));
        long futureSlot = insertSlot(LocalDateTime.now().plusDays(2).withNano(0));
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":" + pastSlot + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":" + futureSlot + ",\"serviceId\":3}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":\"abc\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class));
    }

    @Test
    void appointmentListIsCustomerOnlyScopedToCallerAndValidatesView() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(2).withNano(0));
        MockHttpSession alex = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/appointments").session(alex).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated());
        MockHttpSession jamie = login("jamie@shuttleflow.com", "jamie-test", "/auth/login");
        mvc.perform(get("/appointments").session(jamie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/appointments?view=everything").session(alex)).andExpect(status().isBadRequest());
        MockHttpSession provider = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/provider/login");
        mvc.perform(get("/appointments").session(provider)).andExpect(status().isForbidden());
    }

    @Test
    void pastBookedAppointmentShowsAsCompletedInHistoryAndCannotBeCancelled() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().minusDays(1).withNano(0));
        jdbc.update("UPDATE availability_slots SET status = 'BOOKED' WHERE slot_id = ?", slotId);
        jdbc.update("INSERT INTO appointments (slot_id, user_id, status, active_slot_id) VALUES (?, 3, 'BOOKED', ?)",
                slotId, slotId);
        long appointmentId = jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id = ?",
                Long.class, slotId);
        MockHttpSession alex = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(get("/appointments?view=history").session(alex)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
        mvc.perform(get("/appointments?view=upcoming").session(alex)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/appointments/" + appointmentId).session(alex)).andExpect(status().isConflict());
        mvc.perform(delete("/appointments/999999").session(alex)).andExpect(status().isNotFound());
        mvc.perform(delete("/appointments/" + appointmentId)).andExpect(status().isUnauthorized());
    }

    @Test
    void providerAvailabilityValidationAndOwnershipStatuses() throws Exception {
        MockHttpSession provider = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/provider/login");
        LocalDateTime start = LocalDateTime.now().plusDays(6).withNano(0);
        mvc.perform(post("/provider/slots").session(provider).contentType("application/json")
                        .content(availability(999, start, start.plusHours(1))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/provider/slots").session(provider).contentType("application/json")
                        .content(availability(1, start, start.minusHours(1))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/provider/slots").session(provider).contentType("application/json")
                        .content(availability(1, LocalDateTime.now().minusDays(1).withNano(0), LocalDateTime.now().withNano(0))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/provider/slots").contentType("application/json")
                        .content(availability(1, start, start.plusHours(1))))
                .andExpect(status().isUnauthorized());
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/provider/slots").session(customer).contentType("application/json")
                        .content(availability(1, start, start.plusHours(1))))
                .andExpect(status().isForbidden());

        long coachSlot = insertCoachSlot(start);
        mvc.perform(delete("/provider/slots/" + coachSlot).session(provider)).andExpect(status().isForbidden());
        mvc.perform(delete("/provider/slots/999999").session(provider)).andExpect(status().isNotFound());
        mvc.perform(delete("/provider/slots/" + coachSlot).session(customer)).andExpect(status().isForbidden());
        mvc.perform(delete("/provider/slots/" + coachSlot)).andExpect(status().isUnauthorized());
    }

    @Test
    void createdAvailabilityAppearsInBrowseAndRemovalHidesIt() throws Exception {
        MockHttpSession provider = login("coach.kim@shuttleflow.com", "coach-kim-test", "/auth/provider/login");
        LocalDateTime start = LocalDateTime.now().plusDays(5).withNano(0);
        String body = mvc.perform(post("/provider/slots").session(provider).contentType("application/json")
                        .content(availability(3, start, start.plusHours(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("Private Coaching Session"))
                .andReturn().getResponse().getContentAsString();
        long slotId = Long.parseLong(body.replaceAll(".*\"slotId\":(\\d+).*", "$1"));
        mvc.perform(get("/slots?sessionType=COACHING")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slotId").value(slotId));
        mvc.perform(delete("/provider/slots/" + slotId).session(provider)).andExpect(status().isNoContent());
        mvc.perform(get("/slots?sessionType=COACHING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removingSlotWithCancelledHistoryKeepsHistoryAndHidesSlot() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(3).withNano(0));
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(post("/appointments").session(customer).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated());
        long appointmentId = jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id = ?",
                Long.class, slotId);
        mvc.perform(delete("/appointments/" + appointmentId).session(customer)).andExpect(status().isNoContent());

        MockHttpSession provider = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/provider/login");
        mvc.perform(delete("/provider/slots/" + slotId).session(provider)).andExpect(status().isNoContent());
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM availability_slots WHERE slot_id = ?",
                String.class, slotId));
        mvc.perform(get("/slots")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/appointments?view=history").session(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void providerSeesOnlyOwnBookingsWithCustomerDetails() throws Exception {
        long courtSlot = insertSlot(LocalDateTime.now().plusDays(2).withNano(0));
        long coachSlot = insertCoachSlot(LocalDateTime.now().plusDays(2).withNano(0));
        MockHttpSession jamie = login("jamie@shuttleflow.com", "jamie-test", "/auth/login");
        for (long slot : new long[] {courtSlot, coachSlot}) {
            mvc.perform(post("/appointments").session(jamie).contentType("application/json")
                            .content("{\"slotId\":" + slot + "}"))
                    .andExpect(status().isCreated());
        }
        MockHttpSession coach = login("coach.kim@shuttleflow.com", "coach-kim-test", "/auth/provider/login");
        mvc.perform(get("/provider/appointments").session(coach)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slotId").value(coachSlot))
                .andExpect(jsonPath("$[0].customerName").value("Jamie Lee"))
                .andExpect(jsonPath("$[0].customerEmail").value("jamie@shuttleflow.com"));
    }

    @Test
    void providerServicesListsOnlyOwnServicesAndIsProviderOnly() throws Exception {
        MockHttpSession court = login("court.manager@shuttleflow.com", "court-manager-test", "/auth/provider/login");
        mvc.perform(get("/provider/services").session(court)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Singles Court Rental"))
                .andExpect(jsonPath("$[1].durationMin").value(60));
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test", "/auth/login");
        mvc.perform(get("/provider/services").session(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/provider/services")).andExpect(status().isUnauthorized());
    }

    private String availability(long serviceId, LocalDateTime start, LocalDateTime end) {
        return "{\"serviceId\":" + serviceId + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    private long insertCoachSlot(LocalDateTime start) {
        jdbc.update("""
                INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)
                VALUES (2, 3, ?, ?, 'OPEN')
                """, start, start.plusHours(1));
        return jdbc.queryForObject("SELECT slot_id FROM availability_slots WHERE provider_id = 2 AND start_time = ?",
                Long.class, start);
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
