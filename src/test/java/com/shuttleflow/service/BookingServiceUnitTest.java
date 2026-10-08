package com.shuttleflow.service;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.AvailabilityRequest;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.repository.AppointmentRepository;
import com.shuttleflow.repository.AppointmentRepository.AppointmentRecord;
import com.shuttleflow.repository.ProviderRepository;
import com.shuttleflow.repository.SlotRepository;
import com.shuttleflow.repository.SlotRepository.SlotRecord;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Service rules in isolation: repositories are mocked, so no database or web layer is involved. */
class BookingServiceUnitTest {

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(2);
    private static final LocalDateTime PAST = LocalDateTime.now().minusDays(2);

    private AppointmentRepository appointments;
    private SlotRepository slots;
    private ProviderRepository providers;
    private AppointmentService appointmentService;
    private ProviderService providerService;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        slots = mock(SlotRepository.class);
        providers = mock(ProviderRepository.class);
        SessionAuth sessionAuth = new SessionAuth();
        appointmentService = new AppointmentService(appointments, slots, sessionAuth);
        providerService = new ProviderService(providers, slots, appointments, sessionAuth);
    }

    // --- booking ---

    @Test
    void validBookingInsertsAppointmentAndMarksSlotBooked() {
        when(slots.findByIdForUpdate(7)).thenReturn(Optional.of(slot(7, 1, FUTURE, "OPEN")));
        when(appointments.insert(7, 3)).thenReturn(50L);
        when(slots.updateStatus(7, "OPEN", "BOOKED")).thenReturn(1);
        AppointmentDto dto = new AppointmentDto(50, 7, "Court 3", "Singles", FUTURE, FUTURE.plusHours(1), "BOOKED");
        when(appointments.findCustomerAppointment(50, 3)).thenReturn(Optional.of(dto));

        assertEquals(dto, appointmentService.book(new BookingRequest(7L, null), customer(3)));
        verify(slots).updateStatus(7, "OPEN", "BOOKED");
    }

    @Test
    void bookingRequiresSignedInCustomer() {
        assertThrows(UnauthorizedException.class,
                () -> appointmentService.book(new BookingRequest(7L, null), new MockHttpSession()));
        assertThrows(ForbiddenException.class,
                () -> appointmentService.book(new BookingRequest(7L, null), provider(1)));
        verify(slots, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void bookingRejectsMissingNonexistentPastAndAlreadyBookedSlots() {
        assertThrows(InvalidRequestException.class,
                () -> appointmentService.book(new BookingRequest(0L, null), customer(3)));
        when(slots.findByIdForUpdate(8)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> appointmentService.book(new BookingRequest(8L, null), customer(3)));
        when(slots.findByIdForUpdate(9)).thenReturn(Optional.of(slot(9, 1, PAST, "OPEN")));
        assertThrows(ConflictException.class,
                () -> appointmentService.book(new BookingRequest(9L, null), customer(3)));
        when(slots.findByIdForUpdate(10)).thenReturn(Optional.of(slot(10, 1, FUTURE, "BOOKED")));
        assertThrows(BookingConflictException.class,
                () -> appointmentService.book(new BookingRequest(10L, null), customer(3)));
        verify(appointments, never()).insert(anyLong(), anyLong());
    }

    @Test
    void uniqueConstraintViolationIsTranslatedToBookingConflict() {
        when(slots.findByIdForUpdate(7)).thenReturn(Optional.of(slot(7, 1, FUTURE, "OPEN")));
        when(appointments.insert(7, 3)).thenThrow(new DuplicateKeyException("appointments_one_active_booking"));

        BookingConflictException e = assertThrows(BookingConflictException.class,
                () -> appointmentService.book(new BookingRequest(7L, null), customer(3)));
        assertEquals("Court is already booked.", e.getMessage());
    }

    @Test
    void lostSlotStatusRaceIsBookingConflict() {
        when(slots.findByIdForUpdate(7)).thenReturn(Optional.of(slot(7, 1, FUTURE, "OPEN")));
        when(appointments.insert(7, 3)).thenReturn(50L);
        when(slots.updateStatus(7, "OPEN", "BOOKED")).thenReturn(0);
        assertThrows(BookingConflictException.class,
                () -> appointmentService.book(new BookingRequest(7L, null), customer(3)));
    }

    // --- cancellation ---

    @Test
    void ownerCancelReopensSlot() {
        when(appointments.findByIdForUpdate(50)).thenReturn(Optional.of(appointment(50, 7, 3, "BOOKED", FUTURE)));
        when(appointments.cancel(50)).thenReturn(1);
        when(slots.updateStatus(7, "BOOKED", "OPEN")).thenReturn(1);

        appointmentService.cancel(50, customer(3));
        verify(slots).updateStatus(7, "BOOKED", "OPEN");
    }

    @Test
    void cancelEnforcesOwnershipStateAndTime() {
        when(appointments.findByIdForUpdate(50)).thenReturn(Optional.of(appointment(50, 7, 3, "BOOKED", FUTURE)));
        assertThrows(ForbiddenException.class, () -> appointmentService.cancel(50, customer(4)));
        when(appointments.findByIdForUpdate(51)).thenReturn(Optional.of(appointment(51, 7, 3, "CANCELLED", FUTURE)));
        assertThrows(ConflictException.class, () -> appointmentService.cancel(51, customer(3)));
        when(appointments.findByIdForUpdate(52)).thenReturn(Optional.of(appointment(52, 7, 3, "BOOKED", PAST)));
        assertThrows(ConflictException.class, () -> appointmentService.cancel(52, customer(3)));
        when(appointments.findByIdForUpdate(53)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> appointmentService.cancel(53, customer(3)));
        assertThrows(ForbiddenException.class, () -> appointmentService.cancel(50, provider(1)));
        verify(appointments, never()).cancel(anyLong());
    }

    // --- provider availability ---

    @Test
    void providerCannotCreateSlotForAnotherProvidersService() {
        when(providers.findServiceProviderId(3)).thenReturn(Optional.of(2L));
        assertThrows(ForbiddenException.class, () -> providerService.create(
                new AvailabilityRequest(3L, FUTURE, FUTURE.plusHours(1)), provider(1)));
    }

    @Test
    void duplicateAvailabilityIsConflict() {
        when(providers.findServiceProviderId(1)).thenReturn(Optional.of(1L));
        when(providers.createSlot(1, 1, FUTURE, FUTURE.plusHours(1)))
                .thenThrow(new DuplicateKeyException("provider_id, start_time"));
        assertThrows(ConflictException.class, () -> providerService.create(
                new AvailabilityRequest(1L, FUTURE, FUTURE.plusHours(1)), provider(1)));
    }

    @Test
    void pastOrInvertedAvailabilityIsRejected() {
        assertThrows(InvalidRequestException.class, () -> providerService.create(
                new AvailabilityRequest(1L, PAST, PAST.plusHours(1)), provider(1)));
        assertThrows(InvalidRequestException.class, () -> providerService.create(
                new AvailabilityRequest(1L, FUTURE, FUTURE), provider(1)));
        assertThrows(InvalidRequestException.class, () -> providerService.create(
                new AvailabilityRequest(null, FUTURE, FUTURE.plusHours(1)), provider(1)));
    }

    @Test
    void removalIsOwnerOnlyAndRefusesBookedSlots() {
        when(slots.findByIdForUpdate(7)).thenReturn(Optional.of(slot(7, 2, FUTURE, "OPEN")));
        assertThrows(ForbiddenException.class, () -> providerService.remove(7, provider(1)));
        when(slots.findByIdForUpdate(8)).thenReturn(Optional.of(slot(8, 1, FUTURE, "BOOKED")));
        assertThrows(ConflictException.class, () -> providerService.remove(8, provider(1)));
        verify(slots, never()).deleteSlot(anyLong());
    }

    @Test
    void removalDeletesUnusedSlotButSoftCancelsSlotWithHistory() {
        when(slots.findByIdForUpdate(7)).thenReturn(Optional.of(slot(7, 1, FUTURE, "OPEN")));
        when(appointments.hasAnyAppointment(7)).thenReturn(false);
        providerService.remove(7, provider(1));
        verify(slots).deleteSlot(7);

        when(slots.findByIdForUpdate(8)).thenReturn(Optional.of(slot(8, 1, FUTURE, "OPEN")));
        when(appointments.hasAnyAppointment(8)).thenReturn(true);
        providerService.remove(8, provider(1));
        verify(slots).updateStatus(8, "OPEN", "CANCELLED");
        verify(slots, never()).deleteSlot(8);
    }

    private static SlotRecord slot(long slotId, long providerId, LocalDateTime start, String status) {
        return new SlotRecord(slotId, providerId, 1, start, start.plusHours(1), status);
    }

    private static AppointmentRecord appointment(long id, long slotId, long userId, String status, LocalDateTime start) {
        return new AppointmentRecord(id, slotId, userId, status, start, 1);
    }

    private static HttpSession customer(long userId) {
        return session(new UserSession(userId, "customer@example.com", "Customer", "CUSTOMER", null));
    }

    private static HttpSession provider(long providerId) {
        return session(new UserSession(providerId, "provider@example.com", "Provider", "PROVIDER", providerId));
    }

    private static HttpSession session(UserSession user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.ATTRIBUTE, user);
        return session;
    }
}
