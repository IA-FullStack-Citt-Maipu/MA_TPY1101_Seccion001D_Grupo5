package com.panol_project.backendpanol.modules.users.domain;

import java.util.UUID;

public record UserAdminManagedUser(
        Long id,
        UUID uuid,
        String name,
        boolean active
) {
}
