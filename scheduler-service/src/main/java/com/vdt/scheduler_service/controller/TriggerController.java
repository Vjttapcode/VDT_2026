package com.vdt.scheduler_service.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.scheduler_service.client.NotificationClient;

@RestController
@RequestMapping("/internal")
public class TriggerController {
    private final NotificationClient notificationClient;

    public TriggerController(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    @PostMapping("/trigger")
    public Map<String, Object> trigger() {
        Map<String, Object> result = notificationClient.trigger();
        return Map.of("status", "forwarded", "notification", result);
    }
}
