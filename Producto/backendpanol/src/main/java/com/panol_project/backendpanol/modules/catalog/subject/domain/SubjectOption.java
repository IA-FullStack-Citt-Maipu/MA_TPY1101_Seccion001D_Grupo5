package com.panol_project.backendpanol.modules.catalog.subject.domain;

import java.util.UUID;

public record SubjectOption(
        Long id,
        UUID uuid,
        String code,
        String name
) {
}
