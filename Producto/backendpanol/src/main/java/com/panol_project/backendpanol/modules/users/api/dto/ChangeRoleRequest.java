package com.panol_project.backendpanol.modules.users.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeRoleRequest(@NotBlank String role) {
}

