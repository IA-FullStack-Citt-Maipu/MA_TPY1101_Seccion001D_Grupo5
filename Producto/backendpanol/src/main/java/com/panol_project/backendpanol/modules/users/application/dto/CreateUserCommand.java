package com.panol_project.backendpanol.modules.users.application.dto;

public record CreateUserCommand(
        String name,
        String rut,
        String email,
        String role,
        String password
) {
}
