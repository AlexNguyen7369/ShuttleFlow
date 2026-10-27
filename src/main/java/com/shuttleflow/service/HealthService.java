package com.shuttleflow.service;

import com.shuttleflow.dto.HealthDto;
import com.shuttleflow.repository.HealthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** "Process is running" is not "app can serve": the check asks the database every time. */
@Service
public class HealthService {
    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final HealthRepository healthRepository;

    public HealthService(HealthRepository healthRepository) {
        this.healthRepository = healthRepository;
    }

    public HealthDto check() {
        try {
            healthRepository.ping();
            return new HealthDto("UP", "UP");
        } catch (DataAccessException e) {
            log.error("Health check: database unreachable: {}", e.getClass().getSimpleName());
            return new HealthDto("DOWN", "DOWN");
        }
    }
}
