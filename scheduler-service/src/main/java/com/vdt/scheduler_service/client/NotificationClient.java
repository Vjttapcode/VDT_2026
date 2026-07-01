package com.vdt.scheduler_service.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private final RestTemplate rt;
    private final String baseUrl;

    public NotificationClient(RestTemplate rt,
            @Value("${notification.service.url:http://localhost:8083}") String baseUrl) {
        this.rt = rt;
        this.baseUrl = baseUrl;
    }

    /** Forward trigger sang notification-service; trả body {status, enqueued}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> trigger() {
        String url = baseUrl + "/internal/trigger";
        log.info("[NotificationClient] POST {}", url);
        return rt.postForObject(url, null, Map.class);
    }
}
