package com.vdt.document_service.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NotificationClient {
    
    private final RestTemplate restTemplate;
    private final String notificationUrl;

    public NotificationClient(RestTemplate restTemplate, @Value("${notification.service.url:http://localhost:8083}") String notificationUrl) {
        this.restTemplate = restTemplate;
        this.notificationUrl = notificationUrl;
    }

    public void sendEmail(String eventType, String payloadJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("eventType", eventType, "payload", payloadJson);
        restTemplate.postForEntity(notificationUrl + "/internal/emails", new HttpEntity<>(body, headers), Void.class);
    }
}
