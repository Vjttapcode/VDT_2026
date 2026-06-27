package com.vdt.auth_service.service;

import java.util.List;
import org.springframework.stereotype.Service;

import com.vdt.auth_service.dto.UserDto;
import com.vdt.auth_service.entity.User;
import com.vdt.auth_service.exception.NotFoundException;
import com.vdt.auth_service.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public List<UserDto> findAll() {
        return userRepo.findAll().stream().map(UserDto::from).toList();
    }

    public UserDto update(Long id, UserDto dto) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user id=" + id));
        user.setFullName(dto.fullName());
        if (dto.isActive() != null) user.setIsActive(dto.isActive());
        return UserDto.from(userRepo.save(user));
    }
}
