package com.vdt.auth_service.service;

@Service
public class DepartmentService {
    private final DepartmentRepository deptRepo;
    private final CompanyRepository companyRepo;

    public DepartmentService(DepartmentRepository deptRepo, CompanyRepository companyRepo){
        this.deptRepo = deptRepo;
        this.companyRepo = companyRepo;
    }

    public List<DepartmentDto> findALl(){
        return deptRepo.findAll().stream()
                .map(d -> new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.getCompanyId()))
                .toList();
    }

    public DepartmentDto create(DepartmentDto dto){
        if(companyRepo.findById(dto.companyId()).isEmpty())
            throw new BusinessException("Công ty không tồn tại: " + dto.companyId());
        if(deptRepo.findByCode(dto.code()).isPresent())
            throw new BusinessException("Mã công ty đã tồn tại: " + dto.code());
        Department d = deptRepo.save(Department.builder()
                .name(dto.name()).code(dto.code()).companyId(dto.companyId()).build());
        return new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.getCompanyId());
    }
}