package com.vdt.document_service.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RenewRequest(
    @NotNull @Future LocalDate newExpiryDate
) {

}
