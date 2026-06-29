package com.panol_project.backendpanol.modules.auth.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class BotTokenScopeValidatorTest {

    @Test
    void botTokenConAudienceEsperadoDebeSerValido() {
        BotTokenScopeValidator validator = new BotTokenScopeValidator("bot-panol");

        var result = validator.validate(jwtWithClaims("bot-panol", List.of("bot-panol")));

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void botTokenConAudienceDistintoDebeSerInvalido() {
        BotTokenScopeValidator validator = new BotTokenScopeValidator("bot-panol");

        var result = validator.validate(jwtWithClaims("bot-panol", List.of("otro-audience")));

        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void tokenWebSinTokenUseNoDebeSerAfectado() {
        BotTokenScopeValidator validator = new BotTokenScopeValidator("bot-panol");

        var result = validator.validate(jwtWithClaims(null, List.of()));

        assertTrue(result.getErrors().isEmpty());
    }

    private Jwt jwtWithClaims(String tokenUse, List<String> audience) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("11111111-1111-1111-1111-111111111111")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        if (tokenUse != null) {
            builder.claim("token_use", tokenUse);
        }
        if (!audience.isEmpty()) {
            builder.audience(audience);
        }
        return builder.build();
    }
}
