package com.shuttleflow.controller;

import com.shuttleflow.dto.NotificationRequest;
import com.shuttleflow.service.MockNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The other side of the distribution boundary: 202 for a new notification, 200 for a repeat key. */
@RestController
public class MockNotificationController {
    private final MockNotificationService mockNotificationService;

    public MockNotificationController(MockNotificationService mockNotificationService) {
        this.mockNotificationService = mockNotificationService;
    }

    @PostMapping("/mock-notifications")
    public ResponseEntity<Void> receive(@RequestBody NotificationRequest request) {
        boolean isNew = mockNotificationService.receive(request);
        return ResponseEntity.status(isNew ? 202 : 200).build();
    }
}
