package com.panol_project.backendpanol.modules.email.domain;

public record RenderedEmail(
        String subject,
        String html
) {
}
