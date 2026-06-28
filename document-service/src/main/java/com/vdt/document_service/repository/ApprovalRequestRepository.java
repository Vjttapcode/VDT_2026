package com.vdt.document_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.document_service.entity.ApprovalRequest;


public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long>{
    List<ApprovalRequest> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
