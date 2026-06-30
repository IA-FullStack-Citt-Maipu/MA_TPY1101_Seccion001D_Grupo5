package com.panol_project.backendpanol.modules.users.application.dto;

public record UpdateUserCommand(
        String name,
        String rut,
        String email
) {
}
