package com.vdt.auth_service.repository;

import com.vdt.auth_service.entity.Role;
import com.vdt.auth_service.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    // Dùng cho internal endpoints (Ngày 2): tìm manager theo Trung tâm / Công ty
    Optional<User> findFirstByDepartmentIdAndRole(Long departmentId, Role role);

    Optional<User> findFirstByCompanyIdAndRole(Long companyId, Role role);
}
