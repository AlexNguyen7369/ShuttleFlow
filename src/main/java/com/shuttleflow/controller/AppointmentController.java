package com.shuttleflow.controller;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.service.AppointmentService;
import jakarta.servlet.http.HttpSession;
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
    public ResponseEntity<AppointmentDto> book(@RequestBody BookingRequest request, HttpSession session) {
        return ResponseEntity.status(201).body(appointmentService.book(request, session));
    }

    @GetMapping("/appointments")
    public List<AppointmentDto> list(@RequestParam(required = false) String view, HttpSession session) {
        return appointmentService.list(view, session);
    }

    @DeleteMapping("/appointments/{appointmentId}")
    public ResponseEntity<Void> cancel(@PathVariable long appointmentId, HttpSession session) {
        appointmentService.cancel(appointmentId, session);
        return ResponseEntity.noContent().build();
    }
}
