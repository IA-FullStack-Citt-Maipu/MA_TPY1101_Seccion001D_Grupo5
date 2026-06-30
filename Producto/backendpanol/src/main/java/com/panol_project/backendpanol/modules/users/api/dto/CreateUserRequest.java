package com.panol_project.backendpanol.modules.users.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank String rut,
        @NotBlank @Email String email,
        @NotBlank String role,
        @NotBlank String password
) {
}

