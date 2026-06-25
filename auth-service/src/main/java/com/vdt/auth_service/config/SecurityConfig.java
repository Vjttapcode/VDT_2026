package com.vdt.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.vdt.auth_service.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login").permitAll()
                // /internal/** chỉ gọi trong docker network (không expose qua Nginx) -> để mở
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/register").hasRole("ADMIN")
                .requestMatchers("/users/**").hasRole("ADMIN")
                .requestMatchers("/companies/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/departments").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/departments")
                    .hasAnyRole("ADMIN", "MANAGER_COMPANY", "MANAGER_CENTER")
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
