package com.shuttleflow.controller;

import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.service.SlotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;

@RestController
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/slots")
    public List<SlotDto> slots(@RequestParam(required = false) Long providerId,
                               @RequestParam(required = false) String sessionType,
                               @RequestParam(required = false) Long serviceId,
                               @RequestParam(required = false) LocalDate date,
                               @RequestParam(defaultValue = "1") int page) {
        return slotService.getAvailableSlots(providerId, sessionType, serviceId, date, page);
    }
}
