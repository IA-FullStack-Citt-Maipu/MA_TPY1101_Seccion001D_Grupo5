package com.panol_project.backendpanol.modules.catalog.subject.api.dto;

import java.util.UUID;

public record SubjectSelectorV2Response(
        Long id,
        UUID uuid,
        String code,
        String name
) {
}
