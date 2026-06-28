package com.vdt.document_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.document_service.entity.Document;


public interface DocumentRepository extends  JpaRepository<Document, Long>{
    List<Document> findByOwnerId(Long ownerId);

    List<Document> findByDepartmentId(Long departmentId);

    List<Document> findByCompanyId(Long companyId);
}

