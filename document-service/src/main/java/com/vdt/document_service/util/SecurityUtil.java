package com.vdt.document_service.util;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {
    private SecurityUtil() {}

    public static Long currentUserId()      { return (Long) auth().getPrincipal(); }
    public static String currentRole()      { return details().get("role").toString(); }
    public static Long currentDepartmentId(){ return (Long) details().get("departmentId"); }
    public static Long currentCompanyId()   { return (Long) details().get("companyId"); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details() {
        return (Map<String, Object>) auth().getDetails();
    }
    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}