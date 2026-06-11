package com.panol_project.backendpanol.modules.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record UpdateCurrentPasswordRequest(
        @JsonProperty("current_password")
        @NotBlank(message = "current_password es obligatorio")
        String currentPassword,

        @JsonProperty("new_password")
        @NotBlank(message = "new_password es obligatorio")
        String newPassword
) {
}
