package com.vdt.notification_service.client;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.vdt.notification_service.dto.ExpiringDocumentDto;

@Component
public class DocumentClient {
    private static final Logger log = LoggerFactory.getLogger(DocumentClient.class);
    private final RestTemplate rt;
    private final String baseUrl;

    public DocumentClient(RestTemplate rt, @Value("${document.service.url:http://localhost:8082}") String baseUrl) {
        this.rt = rt;
        this.baseUrl = baseUrl;
    }

    public List<ExpiringDocumentDto> getExpiring(int withinDays) {
        ExpiringDocumentDto[] arr = rt.getForObject(
            baseUrl + "/internal/documents/expiring?withinDays=" + withinDays, ExpiringDocumentDto[].class
        );
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    public void patchStatus(Long docId, String status) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            rt.exchange(baseUrl + "/internal/documents/" + docId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("status", status), h), Void.class
            );
        }catch (Exception e) {
            log.warn("[DocumentClient] PATCH status docId = {} {} thất bại: {}", docId, status, e.getMessage());
        }
    }
}
