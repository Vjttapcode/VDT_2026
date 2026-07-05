package com.vdt.auth_service.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

import com.vdt.auth_service.dto.CompanyDto;
import com.vdt.auth_service.entity.Company;
import com.vdt.auth_service.exception.BusinessException;
import com.vdt.auth_service.exception.NotFoundException;
import com.vdt.auth_service.repository.CompanyRepository;
import com.vdt.auth_service.repository.DepartmentRepository;
import com.vdt.auth_service.repository.UserRepository;

@Service
public class CompanyService {
    private final CompanyRepository repo;
    private final DepartmentRepository deptRepo;
    private final UserRepository userRepo;

    public CompanyService(CompanyRepository repo, DepartmentRepository deptRepo, UserRepository userRepo) {
        this.repo = repo;
        this.deptRepo = deptRepo;
        this.userRepo = userRepo;
    }

    public List<CompanyDto> findAll() {
        return repo.findAll().stream()
                .map(c -> new CompanyDto(c.getId(), c.getName(), c.getCode())).toList();
    }

    public CompanyDto create(CompanyDto dto) {
        if (repo.findByCode(dto.code()).isPresent())
            throw new BusinessException("Mã công ty đã tồn tại: " + dto.code());
        Company c = repo.save(Company.builder()
                .name(dto.name())
                .code(dto.code())
                .createdAt(LocalDateTime.now())
                .build());
        return new CompanyDto(c.getId(), c.getName(), c.getCode());
    }

    public CompanyDto update(Long id, CompanyDto dto) {
        Company c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy công ty id=" + id));
        repo.findByCode(dto.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(o -> { throw new BusinessException("Mã công ty đã tồn tại: " + dto.code()); });
        c.setName(dto.name());
        c.setCode(dto.code());
        repo.save(c);
        return new CompanyDto(c.getId(), c.getName(), c.getCode());
    }

    public void delete(Long id) {
        Company c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy công ty id=" + id));
        if (!deptRepo.findByCompanyId(id).isEmpty())
            throw new BusinessException("Không xóa được: công ty còn trung tâm trực thuộc");
        if (userRepo.existsByCompanyId(id))
            throw new BusinessException("Không xóa được: còn người dùng thuộc công ty");
        repo.delete(c);
    }
}
