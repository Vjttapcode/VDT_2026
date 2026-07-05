package com.vdt.document_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vdt.document_service.entity.DocumentRelation;
import com.vdt.document_service.entity.RelationType;

public interface DocumentRelationRepository extends JpaRepository<DocumentRelation, Long> {

    /** Mọi quan hệ liên quan tới văn bản (là văn bản tác động hoặc bị tác động). */
    @Query("""
        SELECT r FROM DocumentRelation r
        WHERE r.fromDocId = :docId OR r.toDocId = :docId
        ORDER BY r.createdAt DESC
        """)
    List<DocumentRelation> findByDoc(@Param("docId") Long docId);

    boolean existsByFromDocIdAndToDocIdAndRelationType(Long fromDocId, Long toDocId, RelationType relationType);

    void deleteByFromDocIdOrToDocId(Long fromDocId, Long toDocId);
}
