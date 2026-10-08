package com.shuttleflow.service;

import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.repository.SlotRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDate;

@Service
public class SlotService {

    static final int PAGE_SIZE = 10;

    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<SlotDto> getAvailableSlots(Long providerId, String sessionType, Long serviceId,
                                           LocalDate date, int page) {
        if (page < 1) {
            throw new InvalidRequestException("page must be 1 or greater.");
        }
        if (providerId != null && providerId < 1) {
            throw new InvalidRequestException("providerId must be a positive number.");
        }
        if (serviceId != null && serviceId < 1) {
            throw new InvalidRequestException("serviceId must be a positive number.");
        }
        if (date != null && date.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("date must be today or later.");
        }
        String providerType = toProviderType(sessionType);
        return slotRepository.findAvailable(providerId, providerType, serviceId, date,
                PAGE_SIZE, (long) (page - 1) * PAGE_SIZE);
    }

    public List<SlotDto> getAvailableSlots(Long providerId, String sessionType, int page) {
        return getAvailableSlots(providerId, sessionType, null, null, page);
    }

    private String toProviderType(String sessionType) {
        if (sessionType == null) {
            return null;
        }
        return switch (sessionType) {
            case "OPEN_PLAY" -> "COURT";
            case "COACHING" -> "COACH";
            default -> throw new InvalidRequestException("sessionType must be OPEN_PLAY or COACHING.");
        };
    }
}
