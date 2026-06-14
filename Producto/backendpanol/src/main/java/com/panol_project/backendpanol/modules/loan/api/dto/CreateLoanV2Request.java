package com.panol_project.backendpanol.modules.loan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateLoanV2Request(
        @JsonProperty("room_uuid")
        @NotNull(message = "room_uuid es obligatorio")
        UUID roomUuid,

        @JsonProperty("subject_uuid")
        UUID subjectUuid,

        @JsonProperty("scheduled_at")
        @NotNull(message = "scheduled_at es obligatorio")
        OffsetDateTime scheduledAt,

        @JsonProperty("expected_return_at")
        OffsetDateTime expectedReturnAt,

        @Size(max = 1000, message = "notes no puede superar 1000 caracteres")
        String notes,

        @NotEmpty(message = "Debes incluir al menos un item solicitado")
        @Valid
        List<CreateLoanItemV2Request> items
) {
}
