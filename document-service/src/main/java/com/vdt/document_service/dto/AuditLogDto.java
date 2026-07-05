package com.vdt.document_service.dto;

import java.time.LocalDateTime;

import com.vdt.document_service.entity.ApprovalRequest;

/** Một dòng lịch sử thay đổi của văn bản (audit log). */
public record AuditLogDto(
        Long id,
        String action,        // CREATE | UPDATE | SUBMIT | APPROVE | REJECT | RENEW | REPLACE
        Long actorId,         // người thực hiện (requester hoặc reviewer)
        String actorName,     // tên người thực hiện (resolve từ auth-service)
        String comment,
        String changes,       // JSON {field: {old, new}} — null nếu không áp dụng
        LocalDateTime createdAt) {

    public static AuditLogDto from(ApprovalRequest a, String actorName) {
        Long actorId = a.getReviewerId() != null ? a.getReviewerId() : a.getRequesterId();
        return new AuditLogDto(a.getId(), a.getAction(), actorId, actorName,
                a.getComment(), a.getChanges(), a.getCreatedAt());
    }
}
