package com.shuttleflow.controller;

import com.shuttleflow.auth.JwtAuthFilter;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.service.AppointmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentDto> book(@RequestBody BookingRequest request, @RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return ResponseEntity.status(201).body(appointmentService.book(request, caller));
    }

    @GetMapping("/appointments")
    public List<AppointmentDto> list(@RequestParam(required = false) String view, @RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return appointmentService.list(view, caller);
    }

    @DeleteMapping("/appointments/{appointmentId}")
    public ResponseEntity<Void> cancel(@PathVariable long appointmentId, @RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        appointmentService.cancel(appointmentId, caller);
        return ResponseEntity.noContent().build();
    }
}
