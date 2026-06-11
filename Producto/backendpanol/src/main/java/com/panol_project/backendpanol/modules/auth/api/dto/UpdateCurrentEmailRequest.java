package com.panol_project.backendpanol.modules.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateCurrentEmailRequest(
        @JsonProperty("email")
        @NotBlank(message = "email es obligatorio")
        @Email(message = "email debe tener un formato valido")
        String email
) {
}
