package com.shuttleflow.service;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.repository.AppointmentRepository;
import com.shuttleflow.repository.SlotRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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

    @Transactional
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
        UserSession user = sessionAuth.requireAuthenticated(session);
        String normalized = view == null || view.isBlank() ? "upcoming" : view;
        if (!normalized.equals("upcoming") && !normalized.equals("history")) {
            throw new InvalidRequestException("view must be upcoming or history.");
        }
        return appointmentRepository.findCustomerAppointments(user.getUserId(), normalized.equals("history"));
    }

    @Transactional
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
        appointmentRepository.cancel(appointmentId);
        if (slotRepository.updateStatus(appointment.slotId(), "BOOKED", "OPEN") != 1) {
            throw new IllegalStateException("Appointment slot could not be reopened.");
        }
    }
}
