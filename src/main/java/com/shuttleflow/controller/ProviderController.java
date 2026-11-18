package com.shuttleflow.controller;

import com.shuttleflow.auth.JwtAuthFilter;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AvailabilityRequest;
import com.shuttleflow.dto.ProviderAppointmentDto;
import com.shuttleflow.dto.ServiceDto;
import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.service.ProviderService;
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
    public ResponseEntity<SlotDto> create(@RequestBody AvailabilityRequest request, @RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return ResponseEntity.status(201).body(providerService.create(request, caller));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<Void> remove(@PathVariable long slotId, @RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        providerService.remove(slotId, caller);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/services")
    public List<ServiceDto> services(@RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return providerService.services(caller);
    }

    @GetMapping("/appointments")
    public List<ProviderAppointmentDto> appointments(@RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return providerService.appointments(caller);
    }
}
