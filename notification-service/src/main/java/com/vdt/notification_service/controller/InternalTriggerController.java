package com.vdt.notification_service.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.notification_service.service.AlertSchedulingService;


@RestController
@RequestMapping("/internal")
public class InternalTriggerController {
    private final AlertSchedulingService scheduling;
    public InternalTriggerController(AlertSchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @PostMapping("/trigger")
    public Map<String, Object> trigger() {
        int enqueued = scheduling.runCheck();
        return Map.of("status", "ok", "enqueued", enqueued);
    }
    
}
