package com.panol_project.backendpanol.modules.catalog.room.api.dto;

import java.util.UUID;

public record RoomSelectorV2Response(
        Long id,
        UUID uuid,
        String name
) {
}
