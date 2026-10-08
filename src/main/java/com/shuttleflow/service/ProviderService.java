package com.shuttleflow.service;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AvailabilityRequest;
import com.shuttleflow.dto.ProviderAppointmentDto;
import com.shuttleflow.dto.ServiceDto;
import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.repository.AppointmentRepository;
import com.shuttleflow.repository.ProviderRepository;
import com.shuttleflow.repository.SlotRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProviderService {
    private final ProviderRepository providerRepository;
    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final SessionAuth sessionAuth;

    public ProviderService(ProviderRepository providerRepository, SlotRepository slotRepository,
                           AppointmentRepository appointmentRepository, SessionAuth sessionAuth) {
        this.providerRepository = providerRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.sessionAuth = sessionAuth;
    }

    @Transactional
    public SlotDto create(AvailabilityRequest request, HttpSession session) {
        UserSession user = sessionAuth.requireProvider(session);
        validate(request);
        long providerId = user.getProviderId();
        long serviceId = request.getServiceId();
        long serviceProvider = providerRepository.findServiceProviderId(serviceId)
                .orElseThrow(() -> new NotFoundException("Service not found."));
        if (serviceProvider != providerId) {
            throw new ForbiddenException();
        }
        try {
            long slotId = providerRepository.createSlot(providerId, serviceId, request.getStartTime(), request.getEndTime());
            return slotRepository.findDtoById(slotId)
                    .orElseThrow(() -> new IllegalStateException("Created slot could not be read."));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A slot already exists for this provider and start time.");
        }
    }

    @Transactional
    public void remove(long slotId, HttpSession session) {
        UserSession user = sessionAuth.requireProvider(session);
        if (slotId < 1) {
            throw new InvalidRequestException("slotId must be a positive number.");
        }
        SlotRepository.SlotRecord slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new NotFoundException("Slot not found."));
        if (slot.providerId() != user.getProviderId()) {
            throw new ForbiddenException();
        }
        if (!"OPEN".equals(slot.status())) {
            throw new ConflictException("Only open slots can be removed.");
        }
        if (appointmentRepository.hasActiveAppointment(slotId)) {
            throw new ConflictException("Booked slots cannot be removed.");
        }
        // Cancelled appointments still reference the slot, so keep it as CANCELLED history
        // instead of deleting it; either way it no longer appears in GET /slots.
        if (appointmentRepository.hasAnyAppointment(slotId)) {
            slotRepository.updateStatus(slotId, "OPEN", "CANCELLED");
        } else {
            slotRepository.deleteSlot(slotId);
        }
    }

    public List<ServiceDto> services(HttpSession session) {
        UserSession user = sessionAuth.requireProvider(session);
        return providerRepository.findServices(user.getProviderId());
    }

    public List<ProviderAppointmentDto> appointments(HttpSession session) {
        UserSession user = sessionAuth.requireProvider(session);
        return appointmentRepository.findProviderAppointments(user.getProviderId());
    }

    private void validate(AvailabilityRequest request) {
        if (request == null || request.getServiceId() == null || request.getServiceId() < 1) {
            throw new InvalidRequestException("serviceId must be a positive number.");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new InvalidRequestException("startTime and endTime are required.");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidRequestException("endTime must be after startTime.");
        }
        if (!request.getStartTime().isAfter(LocalDateTime.now())) {
            throw new InvalidRequestException("startTime must be in the future.");
        }
    }
}
