package com.panol_project.backendpanol.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String rut,
        @NotBlank String password,
        Boolean rememberMe
) {
}

