package com.vdt.auth_service.controller;

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