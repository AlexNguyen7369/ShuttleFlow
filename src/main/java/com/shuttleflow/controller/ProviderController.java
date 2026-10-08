package com.shuttleflow.controller;

import com.shuttleflow.dto.AvailabilityRequest;
import com.shuttleflow.dto.ProviderAppointmentDto;
import com.shuttleflow.dto.ServiceDto;
import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.service.ProviderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/provider")
public class ProviderController {
    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @PostMapping("/slots")
    public ResponseEntity<SlotDto> create(@RequestBody AvailabilityRequest request, HttpSession session) {
        return ResponseEntity.status(201).body(providerService.create(request, session));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<Void> remove(@PathVariable long slotId, HttpSession session) {
        providerService.remove(slotId, session);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/services")
    public List<ServiceDto> services(HttpSession session) {
        return providerService.services(session);
    }

    @GetMapping("/appointments")
    public List<ProviderAppointmentDto> appointments(HttpSession session) {
        return providerService.appointments(session);
    }
}
