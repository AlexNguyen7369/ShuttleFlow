package com.shuttleflow.service;

import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.repository.SlotRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlotService {

    static final int PAGE_SIZE = 10;

    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<SlotDto> getAvailableSlots(Long providerId, String sessionType, int page) {
        if (page < 1) {
            throw new InvalidRequestException("page must be 1 or greater.");
        }
        String providerType = toProviderType(sessionType);
        return slotRepository.findAvailable(providerId, providerType, PAGE_SIZE, (page - 1) * PAGE_SIZE);
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
