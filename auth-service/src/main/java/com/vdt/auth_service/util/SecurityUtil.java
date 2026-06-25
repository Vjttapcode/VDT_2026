package com.vdt.auth_service.util;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {
    private SecurityUtil() {}

    public static Long currentUserId() {
        return (Long) auth().getPrincipal();
    }

    public static String currentRole() {
        return details().get("role").toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details() {
        return (Map<String, Object>) auth().getDetails();
    }

    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}