package com.shuttleflow.ai;

import com.shuttleflow.auth.UserSession;
import com.shuttleflow.service.AiConversationService;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required M4 test: double-booking stays impossible when bookings go through the AI agent's tools.
 * Real services and database; only the model is absent, because the tools are what it would call.
 */
@SpringBootTest
class AgentDoubleBookingTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SlotService slotService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private AiConversationService conversations;
    private long slotId;

    @BeforeEach
    void prepareSlot() {
        slotId = AgentRaceSupport.freshSlot(jdbc);
    }

    @Test
    void twoSessionsProposeAndConfirmTheSameSlotOnlyOneBooks() {
        UserSession alex = AgentRaceSupport.customer(3L);
        UserSession jamie = AgentRaceSupport.customer(4L);
        tools(alex, 1).proposeBooking(slotId);
        tools(jamie, 1).proposeBooking(slotId);

        String first = tools(alex, 2).confirmBooking();
        String second = tools(jamie, 2).confirmBooking();

        assertThat(first).startsWith("Booked:");
        assertThat(second).isEqualTo("Error: Court is already booked.");
        assertThat(AgentRaceSupport.activeBookings(jdbc, slotId)).isEqualTo(1);
    }

    @Test
    void concurrentAgentConfirmsYieldExactlyOneBooking() throws Exception {
        List<String> results = AgentRaceSupport.race(AgentRaceSupport.customers(jdbc, 8), slotId, slotService,
                appointmentService, conversations);

        assertThat(results).filteredOn(r -> r.startsWith("Booked:")).hasSize(1);
        assertThat(results).filteredOn("Error: Court is already booked."::equals).hasSize(7);
        assertThat(AgentRaceSupport.activeBookings(jdbc, slotId)).isEqualTo(1);
    }

    @Test
    void sameTurnConfirmIsRefusedAndNothingIsBooked() {
        BookingTools tools = tools(AgentRaceSupport.customer(3L), 1);
        tools.proposeBooking(slotId);

        assertThat(tools.confirmBooking()).startsWith("Error: no booking is waiting");
        assertThat(AgentRaceSupport.activeBookings(jdbc, slotId)).isZero();
    }

    @Test
    void providerSessionCannotBookThroughTheAgent() {
        UserSession provider = new UserSession(1L, "court.manager@shuttleflow.com", "Court Manager", "PROVIDER", 1L);
        tools(provider, 1).proposeBooking(slotId);

        assertThat(tools(provider, 2).confirmBooking()).startsWith("Error: You do not have permission");
        assertThat(AgentRaceSupport.activeBookings(jdbc, slotId)).isZero();
    }

    private BookingTools tools(UserSession caller, int turn) {
        return new BookingTools(slotService, appointmentService, conversations, caller, caller.getUserId(), turn);
    }
}
