package com.vdt.document_service.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vdt.document_service.entity.Document;
import com.vdt.document_service.entity.DocumentStatus;


public interface DocumentRepository extends  JpaRepository<Document, Long>{
    List<Document> findByOwnerId(Long ownerId);

    List<Document> findByDepartmentId(Long departmentId);

    List<Document> findByCompanyId(Long companyId);

    @Query("""
       SELECT d FROM Document d
       WHERE d.status IN :statuses
         AND d.expiryDate <= :threshold
       ORDER BY d.expiryDate ASC
       """)
    List<Document> findExpiring(@Param("statuses") List<DocumentStatus> statuses,
                            @Param("threshold") LocalDate threshold);
}

