package com.vdt.auth_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.auth_service.dto.DepartmentDto;
import com.vdt.auth_service.dto.InternalUserDto;
import com.vdt.auth_service.entity.Role;
import com.vdt.auth_service.exception.NotFoundException;
import com.vdt.auth_service.repository.UserRepository;
import com.vdt.auth_service.service.DepartmentService;

@RestController
@RequestMapping("/internal")
public class InternalController {
    private final UserRepository userRepo;
    private final DepartmentService deptService;

    public InternalController(UserRepository userRepo, DepartmentService deptService) {
        this.userRepo = userRepo;
        this.deptService = deptService;
    }

    /** Trung tâm theo id — document-service dùng để suy công ty của văn bản cấp Trung tâm. */
    @GetMapping("/departments/{id}")
    public DepartmentDto department(@PathVariable Long id) {
        DepartmentDto d = deptService.findOne(id);
        if (d == null) throw new NotFoundException("Không tìm thấy trung tâm id=" + id);
        return d;
    }

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