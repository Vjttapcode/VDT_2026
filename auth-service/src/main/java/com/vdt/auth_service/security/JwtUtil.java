package com.vdt.auth_service.security;

import com.vdt.auth_service.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Phát hành & xác thực JWT. Claims:
 *   sub          = email
 *   userId       (Long)
 *   role         (String)
 *   departmentId (Long, nullable)
 *   companyId    (Long, nullable)
 * Shared JWT_SECRET với các service khác để chúng verify cục bộ.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        return generateToken(user, user.getCompanyId());
    }

    /**
     * Cho phép truyền companyId đã suy ra (USER/MANAGER_CENTER lấy company của phòng ban)
     * để token luôn mang phạm vi công ty — phục vụ lọc dữ liệu theo cấp Công ty.
     */
    public String generateToken(User user, Long companyId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole().name());
        claims.put("departmentId", user.getDepartmentId()); // nullable
        claims.put("companyId", companyId);                  // nullable, có thể suy từ phòng ban

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserId(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public Long getDepartmentId(String token) {
        return parseClaims(token).get("departmentId", Long.class);
    }

    public Long getCompanyId(String token) {
        return parseClaims(token).get("companyId", Long.class);
    }
}
