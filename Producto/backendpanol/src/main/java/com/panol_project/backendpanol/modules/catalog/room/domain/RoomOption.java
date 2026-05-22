package com.panol_project.backendpanol.modules.catalog.room.domain;

import java.util.UUID;

public record RoomOption(
        Long id,
        UUID uuid,
        String name
) {
}
