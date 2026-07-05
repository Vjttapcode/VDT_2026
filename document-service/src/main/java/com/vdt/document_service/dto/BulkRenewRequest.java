package com.vdt.document_service.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record BulkRenewRequest(
    @NotEmpty List<Long> ids,
    @NotNull @Future LocalDate newExpiryDate
) {}
