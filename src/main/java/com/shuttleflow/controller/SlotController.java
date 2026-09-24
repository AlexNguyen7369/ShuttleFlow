package com.shuttleflow.controller;

import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.service.SlotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/slots")
    public List<SlotDto> slots() {
        return slotService.getAvailableSlots();
    }
}
