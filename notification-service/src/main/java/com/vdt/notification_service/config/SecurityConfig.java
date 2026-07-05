package com.vdt.notification_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.vdt.notification_service.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health").permitAll()   // healthcheck compose/K8s
                .requestMatchers("/internal/**").permitAll()        // gọi nội bộ docker network
                // chỉ ADMIN mới chỉnh ngưỡng + chạy các hành động quản trị cảnh báo
                .requestMatchers("/api/notifications/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/notifications/alert-configs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/notifications/alert-configs/**")
                    .hasAnyRole("ADMIN", "MANAGER_COMPANY", "MANAGER_CENTER")
                // nhật ký cảnh báo: mọi user đã đăng nhập đều xem được (UI lọc theo role)
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
