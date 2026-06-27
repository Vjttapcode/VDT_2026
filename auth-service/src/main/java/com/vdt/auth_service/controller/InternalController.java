package com.vdt.auth_service.controller;

@RestController
@RequestMapping("/internal")
public class InternalController {
    private final UserRepository userRepo;

    public InternalController(UserRepository userRepo) { this.userRepo = userRepo; }

    @GetMapping("/users/{id}")
    public InternalUserDto getUser(@PathVariable Long id) {
        return userRepo.findById(id).map(InternalUserDto::from)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user id=" + id));
    }

    @GetMapping("/manager/center/{deptId}")
    public InternalUserDto centerManager(@PathVariable Long deptId) {
        return userRepo.findFirstByDepartmentIdAndRole(deptId, Role.MANAGER_CENTER)
                .map(InternalUserDto::from)
                .orElseThrow(() -> new NotFoundException("Trung tâm " + deptId + " chưa có MANAGER_CENTER"));
    }

    @GetMapping("/manager/company/{companyId}")
    public InternalUserDto companyManager(@PathVariable Long companyId) {
        return userRepo.findFirstByCompanyIdAndRole(companyId, Role.MANAGER_COMPANY)
                .map(InternalUserDto::from)
                .orElseThrow(() -> new NotFoundException("Công ty " + companyId + " chưa có MANAGER_COMPANY"));
    }

    @GetMapping("/admin")
    public InternalUserDto admin() {
        return userRepo.findByRole(Role.ADMIN).stream().findFirst()
                .map(InternalUserDto::from)
                .orElseThrow(() -> new NotFoundException("Hệ thống chưa có ADMIN"));
    }
}