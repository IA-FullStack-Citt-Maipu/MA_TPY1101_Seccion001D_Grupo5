package com.panol_project.backendpanol.modules.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PasswordRecoveryResetRequest(
        @JsonProperty("reset_token")
        @NotBlank(message = "reset_token es obligatorio")
        String resetToken,

        @JsonProperty("new_password")
        @NotBlank(message = "new_password es obligatorio")
        String newPassword
) {
}
