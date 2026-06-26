package com.vdt.auth_service.service;

@Service
public class UserService {
    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public List<UserDto> findAll() {
        return userRepo.findAll().stream().map(UserDto::from).toList();
    }

    public UserDto update(Long id, UserDto userDto){
        User user = userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user id=" + id));
        user.setFullName(dto.fullName());
        if(dto.isActive() != null) user.setIsActive(dto.isActive());
        return userDto.from(userRepo.save(user));
    }
}