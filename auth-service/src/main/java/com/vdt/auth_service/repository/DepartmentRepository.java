package com.vdt.auth_service.repository;

import com.vdt.auth_service.entity.Department;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByCode(String code);

    List<Department> findByCompanyId(Long companyId);
}
