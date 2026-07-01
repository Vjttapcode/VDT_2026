package com.vdt.notification_service.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.notification_service.client.AuthClient;
import com.vdt.notification_service.service.EmailService;


@RestController
@RequestMapping("/internal")
public class InternalEmailController {
    private final EmailService service;
    private final AuthClient authClient;
    private final ObjectMapper om;

    public InternalEmailController(EmailService service, AuthClient auth, ObjectMapper om) {
        this.service = service;
        this.authClient = auth;
        this.om = om;
    }

    @PostMapping("/emails")
    public void send(@RequestBody Map<String, String> body) throws Exception {
        String eventType = body.get("eventType");
        JsonNode p = om.readTree(body.get("payload"));
        Long docId = p.path("docId").asLong();
        String title = p.path("docTitle").asText();
        String reason = p.has("reason") ? p.get("reason").asText() : null;

        // resolve người nhận từ payload (cần document-service bổ sung ownerId/departmentId — xem ghi chú)
        String to = switch (eventType) {
            case "APPROVAL_REQUEST" -> authClient.centerManagerEmail(p.path("departmentId").asLong());
            case "APPROVED", "REJECTED" -> p.path("ownerEmail").asText(null);
            default -> null;
        };
        if (to != null) service.sendApproval(to, eventType, docId, title, reason);
    }
    
}
