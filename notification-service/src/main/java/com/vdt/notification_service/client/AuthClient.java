package com.vdt.notification_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.vdt.notification_service.dto.AuthUserDto;

@Component
public class AuthClient {
    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);
    private final RestTemplate rt;
    private final String authUrl;

    public AuthClient(RestTemplate rt, @Value("${auth.service.url:http://localhost:8081}") String authUrl) {
        this.rt = rt;
        this.authUrl = authUrl;
    }

    public String centerManagerEmail(Long deptId) {
        return email("/internal/manager/center/" + deptId);
    }
    
    public String companyManagerEmail(Long companyId) {
        return email("/internal/manager/company/" + companyId);
    }

    public String adminEmail() { 
        return email("/internal/admin");
    }

    private String email(String path) {
        try {
            AuthUserDto u = rt.getForObject(authUrl + path, AuthUserDto.class);
            return u == null ? null : u.email();
        } catch (Exception e) {
            log.warn("[AuthClient] {} lỗi: {}",path, e.getMessage());
            return null;
        }
    }
}
