package com.panol_project.backendpanol.modules.catalog.category.api.dto;

import java.util.UUID;

public record CategoryAssociationV2Response(
        UUID categoryUuid,
        Integer implementCount,
        Boolean canDelete
) {
}
