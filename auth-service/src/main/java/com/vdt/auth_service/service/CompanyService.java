package com.vdt.auth_service.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

import com.vdt.auth_service.dto.CompanyDto;
import com.vdt.auth_service.entity.Company;
import com.vdt.auth_service.exception.BusinessException;
import com.vdt.auth_service.repository.CompanyRepository;

@Service
public class CompanyService {
    private final CompanyRepository repo;

    public CompanyService(CompanyRepository repo) {
        this.repo = repo;
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
}
