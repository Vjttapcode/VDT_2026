package com.vdt.document_service.dto;

import com.vdt.document_service.entity.DocumentStatus;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull DocumentStatus status) {}
