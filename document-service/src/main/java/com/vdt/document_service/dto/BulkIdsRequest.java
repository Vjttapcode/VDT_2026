package com.vdt.document_service.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record BulkIdsRequest(@NotEmpty List<Long> ids) {}
