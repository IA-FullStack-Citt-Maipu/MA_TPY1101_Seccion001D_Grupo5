package com.panol_project.backendpanol.modules.auth.infrastructure;

import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TokenRevocationValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenRevocationPort tokenRevocationRepository;

    public TokenRevocationValidator(TokenRevocationPort tokenRevocationRepository) {
        this.tokenRevocationRepository = tokenRevocationRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (tokenRevocationRepository.isRevoked(token.getId())) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token invalidado", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}

