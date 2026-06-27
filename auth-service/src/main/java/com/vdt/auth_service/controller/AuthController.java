package com.vdt.auth_service.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.auth_service.dto.LoginRequest;
import com.vdt.auth_service.dto.LoginResponse;
import com.vdt.auth_service.dto.RegisterRequest;
import com.vdt.auth_service.dto.UserDto;
import com.vdt.auth_service.service.AuthService;

@RestController
public class AuthController{
    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req){
        return authService.login(req);
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest req){
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }
}