package com.vdt.auth_service.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

import com.vdt.auth_service.dto.DepartmentDto;
import com.vdt.auth_service.entity.Company;
import com.vdt.auth_service.entity.Department;
import com.vdt.auth_service.exception.BusinessException;
import com.vdt.auth_service.repository.CompanyRepository;
import com.vdt.auth_service.repository.DepartmentRepository;

@Service
public class DepartmentService {
    private final DepartmentRepository deptRepo;
    private final CompanyRepository companyRepo;

    public DepartmentService(DepartmentRepository deptRepo, CompanyRepository companyRepo) {
        this.deptRepo = deptRepo;
        this.companyRepo = companyRepo;
    }

    public List<DepartmentDto> findAll() {
        return deptRepo.findAll().stream()
                .map(d -> new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.getCompany().getId()))
                .toList();
    }

    public DepartmentDto create(DepartmentDto dto) {
        Company company = companyRepo.findById(dto.companyId())
                .orElseThrow(() -> new BusinessException("Công ty không tồn tại: " + dto.companyId()));
        if (deptRepo.findByCode(dto.code()).isPresent())
            throw new BusinessException("Mã trung tâm đã tồn tại: " + dto.code());
        Department d = deptRepo.save(Department.builder()
                .name(dto.name())
                .code(dto.code())
                .company(company)
                .createdAt(LocalDateTime.now())
                .build());
        return new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.getCompany().getId());
    }
}
