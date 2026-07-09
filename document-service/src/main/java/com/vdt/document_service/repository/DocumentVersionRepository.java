package com.vdt.document_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vdt.document_service.entity.DocumentVersion;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    /** Lịch sử phiên bản của một văn bản — mới nhất trước. */
    List<DocumentVersion> findByDocumentIdOrderByVersionMajorDescVersionMinorDesc(Long documentId);

    void deleteByDocumentId(Long documentId);
}
