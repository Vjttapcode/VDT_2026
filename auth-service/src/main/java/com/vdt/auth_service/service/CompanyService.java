package com.vdt.auth_service.service;

@Service
public class CompanyService {
    private final CompanyRepository repo;

    public CompanyService(CompanyRepository repo) {
        this.companyRepo = repo;
    }

    public List<CompanyDto> findAll() {
        return repo.findAll().stream()
                .map(c -> new CompanyDto(c.getId(), c.getName(), c.getCode())).toList();
    }

    public companyDto create(CompanyDto dto){
        if(repo.findByCode(dto.code()).isPresent())
            throw new BusinessException("Mã công ty đã tồn tại: " + dto.code());
        Company c = repo.save(Company.builder().name(dto.name()).code(dto.code()).build());
        return new CompanyDto(c.getId(), c.getName(), c.getCode());
    }
}