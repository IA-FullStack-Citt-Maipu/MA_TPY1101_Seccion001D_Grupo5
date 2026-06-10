package com.panol_project.backendpanol.modules.auth.api;

import com.panol_project.backendpanol.modules.auth.api.dto.CurrentUserResponse;
import com.panol_project.backendpanol.modules.auth.api.dto.LoginRequest;
import com.panol_project.backendpanol.modules.auth.api.dto.LoginResponse;
import com.panol_project.backendpanol.modules.auth.api.dto.LoginUserResponse;
import com.panol_project.backendpanol.modules.auth.api.dto.UpdateCurrentEmailRequest;
import com.panol_project.backendpanol.modules.auth.api.dto.UpdateCurrentPasswordRequest;
import com.panol_project.backendpanol.modules.auth.application.AuthService;
import com.panol_project.backendpanol.modules.auth.application.dto.ChangeCurrentPasswordCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.LoginCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.UpdateCurrentEmailCommand;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthV2Controller {

    private final AuthService authService;
    private final CurrentUserUuidResolver currentUserUuidResolver;

    public AuthV2Controller(AuthService authService, CurrentUserUuidResolver currentUserUuidResolver) {
        this.authService = authService;
        this.currentUserUuidResolver = currentUserUuidResolver;
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

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    CurrentUserResponse getCurrentUser(Authentication authentication) {
        return toCurrentUserResponse(authService.getCurrentUser(resolveCurrentUserUuid(authentication)));
    }

    @PatchMapping("/me/email")
    @PreAuthorize("isAuthenticated()")
    CurrentUserResponse updateCurrentUserEmail(
            @Valid @RequestBody UpdateCurrentEmailRequest request,
            Authentication authentication
    ) {
        return toCurrentUserResponse(authService.updateCurrentUserEmail(
                resolveCurrentUserUuid(authentication),
                new UpdateCurrentEmailCommand(request.email())
        ));
    }

    @PatchMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> updateCurrentUserPassword(
            @Valid @RequestBody UpdateCurrentPasswordRequest request,
            Authentication authentication
    ) {
        authService.updateCurrentUserPassword(
                resolveCurrentUserUuid(authentication),
                new ChangeCurrentPasswordCommand(request.currentPassword(), request.newPassword())
        );
        return ResponseEntity.noContent().build();
    }

    private UUID resolveCurrentUserUuid(Authentication authentication) {
        return currentUserUuidResolver.resolveCurrentUserUuid(authentication)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));
    }

    private CurrentUserResponse toCurrentUserResponse(com.panol_project.backendpanol.modules.auth.application.dto.AuthenticatedUserSummary user) {
        return new CurrentUserResponse(
                user.id(),
                user.name(),
                user.email(),
                user.role()
        );
    }
}
