package com.shuttleflow.controller;

import com.shuttleflow.dto.MetricsDto;
import com.shuttleflow.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public MetricsDto metrics() {
        return metricsService.snapshot();
    }
}
