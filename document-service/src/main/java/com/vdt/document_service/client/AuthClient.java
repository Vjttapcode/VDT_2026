package com.vdt.document_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.vdt.document_service.dto.AuthUserDto;

@Component
public class AuthClient {
    
    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);

    private final RestTemplate restTemplate;
    private final String authUrl;

    public AuthClient(RestTemplate restTemplate, @Value("${auth.service.url:http://localhost:8081}") String authUrl) {
        this.restTemplate = restTemplate;
        this.authUrl = authUrl;
    }

    public String getEmail(Long userId) {
        if(userId == null) return null;
        try {
            AuthUserDto u = restTemplate.getForObject(authUrl + "/internal/users/" + userId, AuthUserDto.class);
            return u == null ? null : u.email();
        } catch (Exception e) {
            log.warn("[AuthClient] không lấy được email userId={} err={}", userId, e.getMessage());
            return null;
        }
    }

    /** Tên đầy đủ của người phụ trách — dùng để hiển thị & tra cứu theo người phụ trách. */
    public String getName(Long userId) {
        if(userId == null) return null;
        try {
            AuthUserDto u = restTemplate.getForObject(authUrl + "/internal/users/" + userId, AuthUserDto.class);
            return u == null ? null : u.fullName();
        } catch (Exception e) {
            log.warn("[AuthClient] không lấy được tên userId={} err={}", userId, e.getMessage());
            return null;
        }
    }

    
}
