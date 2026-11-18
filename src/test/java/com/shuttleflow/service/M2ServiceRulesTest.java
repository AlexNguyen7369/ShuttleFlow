package com.shuttleflow.service;

import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AvailabilityRequest;
import com.shuttleflow.dto.BookingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class M2ServiceRulesTest {
    @Autowired private AppointmentService appointmentService;
    @Autowired private ProviderService providerService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clearSlots() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
    }

    @Test
    void bookingRejectsMissingSlotAndProviderRole() {
        assertThrows(InvalidRequestException.class, () -> appointmentService.book(new BookingRequest(null, null), customer(3)));
        assertThrows(ForbiddenException.class, () -> appointmentService.book(new BookingRequest(1L, null), provider(1)));
    }

    @Test
    void providerAvailabilityValidatesTimeRangeAndCustomerRole() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertThrows(InvalidRequestException.class, () -> providerService.create(
                new AvailabilityRequest(1L, start.plusHours(1), start), provider(1)));
        assertThrows(ForbiddenException.class, () -> providerService.create(
                new AvailabilityRequest(1L, start, start.plusHours(1)), customer(3)));
    }

    @Test
    void unauthenticatedServicesRejectRequests() {
        assertThrows(UnauthorizedException.class, () -> appointmentService.list("upcoming", null));
        assertThrows(UnauthorizedException.class, () -> providerService.appointments(null));
    }

    private UserSession customer(long id) {
        return new UserSession(id, "customer@example.com", "Customer", "CUSTOMER", null);
    }

    private UserSession provider(long id) {
        return new UserSession(id, "provider@example.com", "Provider", "PROVIDER", id);
    }

}
