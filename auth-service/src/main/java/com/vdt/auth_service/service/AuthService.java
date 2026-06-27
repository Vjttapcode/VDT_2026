package com.vdt.auth_service.service;

import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.vdt.auth_service.dto.LoginRequest;
import com.vdt.auth_service.dto.LoginResponse;
import com.vdt.auth_service.dto.RegisterRequest;
import com.vdt.auth_service.dto.UserDto;
import com.vdt.auth_service.entity.Role;
import com.vdt.auth_service.entity.User;
import com.vdt.auth_service.exception.BusinessException;
import com.vdt.auth_service.repository.UserRepository;
import com.vdt.auth_service.security.JwtUtil;

@Service
public class AuthService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil){
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest req){
        User user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new BusinessException("Email hoặc mật khẩu không đúng"));
        if(!Boolean.TRUE.equals(user.getIsActive())){
            throw new BusinessException("Tài khoản đã bị khoá");
        }
        if(!encoder.matches(req.password(), user.getPasswordHash())){
            throw new BusinessException("Email hoặc mật khẩu không đúng");
        }
        String token = jwtUtil.generateToken(user);
        return new LoginResponse(token, user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getDepartmentId(), user.getCompanyId());
    }

    public UserDto register(RegisterRequest req){
        if(userRepo.existsByEmail(req.email()))
            throw new BusinessException("Email đã tồn tại");

        Role role = parseRole(req.role());
        validateOrgScope(role, req.departmentId(), req.companyId());

        User user = User.builder()
                .email(req.email())
                .passwordHash(encoder.encode(req.password()))
                .fullName(req.fullName())
                .role(role)
                .departmentId(req.departmentId())
                .companyId(req.companyId())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        return UserDto.from(userRepo.save(user));
    }

    private Role parseRole(String role){
        try{
            return Role.valueOf(role);
        }catch (IllegalArgumentException e){
            throw new BusinessException("Role không hợp lệ: " + role);
        }
    }

    private void validateOrgScope(Role role, Long deptId, Long companyId){
        boolean ok = switch (role){
            case ADMIN -> deptId == null && companyId == null;
            case MANAGER_COMPANY -> deptId == null && companyId != null;
            case MANAGER_CENTER, USER -> deptId != null && companyId == null;
        };
        if(!ok) throw new BusinessException(
                "departmentId/companyId không khớp với role " + role);
    }
}
