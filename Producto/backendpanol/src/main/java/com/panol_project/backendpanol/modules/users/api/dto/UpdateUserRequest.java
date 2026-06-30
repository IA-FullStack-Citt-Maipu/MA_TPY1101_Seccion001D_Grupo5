package com.panol_project.backendpanol.modules.users.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank String name,
        @NotBlank String rut,
        @NotBlank @Email String email
) {
}
