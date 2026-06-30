package com.panol_project.backendpanol.modules.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordRecoveryVerifyResponse(
        @JsonProperty("reset_token")
        String resetToken,

        @JsonProperty("expires_in_seconds")
        int expiresInSeconds
) {
}
