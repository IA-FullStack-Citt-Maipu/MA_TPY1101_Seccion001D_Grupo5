package com.panol_project.backendpanol.modules.auth.infrastructure;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class BotTokenScopeValidator implements OAuth2TokenValidator<Jwt> {

    private static final String BOT_TOKEN_USE = "bot-panol";
    private static final OAuth2Error INVALID_BOT_AUDIENCE = new OAuth2Error(
            "invalid_token",
            "Token del asistente con audience invalido",
            null
    );

    private final String expectedAudience;

    public BotTokenScopeValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience == null ? "" : expectedAudience.trim();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object tokenUse = token.getClaims().get("token_use");
        if (!BOT_TOKEN_USE.equals(tokenUse)) {
            return OAuth2TokenValidatorResult.success();
        }

        if (expectedAudience.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }

        List<String> audience = token.getAudience();
        if (audience == null || !audience.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.failure(INVALID_BOT_AUDIENCE);
        }

        return OAuth2TokenValidatorResult.success();
    }
}
