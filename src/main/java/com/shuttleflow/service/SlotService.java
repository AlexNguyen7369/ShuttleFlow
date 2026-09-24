package com.shuttleflow.service;

import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.repository.SlotRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlotService {

    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<SlotDto> getAvailableSlots() {
        return slotRepository.findAvailable();
    }
}
