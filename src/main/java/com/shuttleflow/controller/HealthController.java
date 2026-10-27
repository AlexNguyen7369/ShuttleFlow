package com.shuttleflow.controller;

import com.shuttleflow.dto.HealthDto;
import com.shuttleflow.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 200 UP / 503 DOWN: automated checkers read the status code, not the body. */
@RestController
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthDto> health() {
        HealthDto health = healthService.check();
        return ResponseEntity.status("UP".equals(health.getStatus()) ? 200 : 503).body(health);
    }
}
