package com.panol_project.backendpanol.modules.auth.api;

import com.panol_project.backendpanol.modules.auth.api.dto.LoginRequest;
import com.panol_project.backendpanol.modules.auth.api.dto.LoginResponse;
import com.panol_project.backendpanol.modules.auth.api.dto.LoginUserResponse;
import com.panol_project.backendpanol.modules.auth.application.AuthService;
import com.panol_project.backendpanol.modules.auth.application.dto.LoginCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthV2Controller {

    private final AuthService authService;

    public AuthV2Controller(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var result = authService.login(new LoginCommand(request.rut(), request.password()));
        return new LoginResponse(
                result.accessToken(),
                result.role(),
                result.expiresInSeconds(),
                new LoginUserResponse(
                        result.user().id(),
                        result.user().name(),
                        result.user().email(),
                        result.user().role()
                )
        );
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt);
        return ResponseEntity.noContent().build();
    }
}
