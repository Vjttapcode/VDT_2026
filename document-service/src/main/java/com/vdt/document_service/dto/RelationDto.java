package com.vdt.document_service.dto;

import java.time.LocalDateTime;

/** Một quan hệ của văn bản, đã resolve chiều + văn bản đối tác để hiển thị. */
public record RelationDto(
        Long id,
        String type,           // REPLACE | REPEAL | AMEND
        String direction,      // OUTGOING = văn bản này tác động; INCOMING = bị tác động
        Long otherDocId,
        String otherDocTitle,
        LocalDateTime createdAt) {
}
