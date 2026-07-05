package com.vdt.auth_service.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

import com.vdt.auth_service.dto.DepartmentDto;
import com.vdt.auth_service.entity.Company;
import com.vdt.auth_service.entity.Department;
import com.vdt.auth_service.exception.BusinessException;
import com.vdt.auth_service.exception.NotFoundException;
import com.vdt.auth_service.repository.CompanyRepository;
import com.vdt.auth_service.repository.DepartmentRepository;
import com.vdt.auth_service.repository.UserRepository;

@Service
public class DepartmentService {
    private final DepartmentRepository deptRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;

    public DepartmentService(DepartmentRepository deptRepo, CompanyRepository companyRepo, UserRepository userRepo) {
        this.deptRepo = deptRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
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

    public DepartmentDto update(Long id, DepartmentDto dto) {
        Department d = deptRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy trung tâm id=" + id));
        Company company = companyRepo.findById(dto.companyId())
                .orElseThrow(() -> new BusinessException("Công ty không tồn tại: " + dto.companyId()));
        deptRepo.findByCode(dto.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(o -> { throw new BusinessException("Mã trung tâm đã tồn tại: " + dto.code()); });
        d.setName(dto.name());
        d.setCode(dto.code());
        d.setCompany(company);
        deptRepo.save(d);
        return new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.getCompany().getId());
    }

    public void delete(Long id) {
        Department d = deptRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy trung tâm id=" + id));
        if (userRepo.existsByDepartmentId(id))
            throw new BusinessException("Không xóa được: còn người dùng thuộc trung tâm");
        deptRepo.delete(d);
    }
}
