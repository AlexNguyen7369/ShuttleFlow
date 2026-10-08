package com.shuttleflow.service;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.repository.AppointmentRepository;
import com.shuttleflow.repository.SlotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final SessionAuth sessionAuth;

    public AppointmentService(AppointmentRepository appointmentRepository, SlotRepository slotRepository,
                              SessionAuth sessionAuth) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.sessionAuth = sessionAuth;
    }

    /**
     * Books one slot in a single READ COMMITTED transaction (atomic: appointment insert and slot
     * status change commit or roll back together).
     *
     * Concurrency: {@code SELECT ... FOR UPDATE} makes a competing booking for the same slot wait
     * until this transaction ends; it then re-reads the slot as BOOKED and gets a 409. The
     * appointments_one_active_booking UNIQUE key is the database backstop if any path skips the
     * lock, and its violation is translated to the same 409. No retry: the loser's outcome is
     * final (the slot is taken), so retrying could never succeed.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDto book(BookingRequest request, HttpSession session) {
        UserSession user = sessionAuth.requireCustomer(session);
        if (request == null || request.getSlotId() == null || request.getSlotId() < 1) {
            throw new InvalidRequestException("slotId must be a positive number.");
        }
        if (request.getServiceId() != null && request.getServiceId() < 1) {
            throw new InvalidRequestException("serviceId must be a positive number.");
        }

        SlotRepository.SlotRecord slot = slotRepository.findByIdForUpdate(request.getSlotId())
                .orElseThrow(() -> new NotFoundException("Slot not found."));
        if (request.getServiceId() != null && request.getServiceId() != slot.serviceId()) {
            throw new InvalidRequestException("serviceId does not match the selected slot.");
        }
        if (slot.startTime().isBefore(LocalDateTime.now())) {
            throw new ConflictException("This slot has already started.");
        }
        if (!"OPEN".equals(slot.status())) {
            throw new BookingConflictException();
        }

        try {
            long appointmentId = appointmentRepository.insert(slot.slotId(), user.getUserId());
            if (slotRepository.updateStatus(slot.slotId(), "OPEN", "BOOKED") != 1) {
                throw new BookingConflictException();
            }
            return appointmentRepository.findCustomerAppointment(appointmentId, user.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Created appointment could not be read."));
        } catch (DataIntegrityViolationException e) {
            throw new BookingConflictException();
        }
    }

    public List<AppointmentDto> list(String view, HttpSession session) {
        UserSession user = sessionAuth.requireCustomer(session);
        String normalized = view == null || view.isBlank() ? "upcoming" : view;
        if (!normalized.equals("upcoming") && !normalized.equals("history")) {
            throw new InvalidRequestException("view must be upcoming or history.");
        }
        return appointmentRepository.findCustomerAppointments(user.getUserId(), normalized.equals("history"));
    }

    /** Cancels an owned upcoming appointment and reopens its slot in one transaction. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(long appointmentId, HttpSession session) {
        UserSession user = sessionAuth.requireCustomer(session);
        if (appointmentId < 1) {
            throw new InvalidRequestException("appointmentId must be a positive number.");
        }
        AppointmentRepository.AppointmentRecord appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new NotFoundException("Appointment not found."));
        if (appointment.userId() != user.getUserId()) {
            throw new ForbiddenException();
        }
        if (!"BOOKED".equals(appointment.status())) {
            throw new ConflictException("Appointment is already cancelled.");
        }
        if (appointment.startTime().isBefore(LocalDateTime.now())) {
            throw new ConflictException("Past appointments cannot be cancelled.");
        }
        if (appointmentRepository.cancel(appointmentId) != 1) {
            throw new ConflictException("Appointment is already cancelled.");
        }
        if (slotRepository.updateStatus(appointment.slotId(), "BOOKED", "OPEN") != 1) {
            throw new IllegalStateException("Appointment slot could not be reopened.");
        }
    }
}
