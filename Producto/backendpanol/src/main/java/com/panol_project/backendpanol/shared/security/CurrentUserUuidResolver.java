package com.panol_project.backendpanol.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserUuidResolver {

    public Optional<UUID> resolveCurrentUserUuid() {
        return resolveCurrentUserUuid(SecurityContextHolder.getContext().getAuthentication());
    }

    public Optional<UUID> resolveCurrentUserUuid(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        UUID fromName = parseUuid(authentication.getName());
        if (fromName != null) {
            return Optional.of(fromName);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            UUID fromSubject = parseUuid(jwt.getSubject());
            if (fromSubject != null) {
                return Optional.of(fromSubject);
            }
            return Optional.ofNullable(parseUuid(jwt.getClaimAsString("user_uuid")));
        }

        if (principal instanceof String value) {
            return Optional.ofNullable(parseUuid(value));
        }

        return Optional.empty();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
