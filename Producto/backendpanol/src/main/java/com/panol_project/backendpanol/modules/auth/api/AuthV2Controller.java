package com.panol_project.backendpanol.modules.auth.api;

import com.panol_project.backendpanol.modules.auth.api.dto.BotAccessTokenResponse;
import com.panol_project.backendpanol.modules.auth.api.dto.CurrentUserResponse;
import com.panol_project.backendpanol.modules.auth.api.dto.CurrentUserSessionResponse;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthV2Controller {

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final CurrentUserUuidResolver currentUserUuidResolver;

    public AuthV2Controller(
            AuthService authService,
            AuthCookieService authCookieService,
            CurrentUserUuidResolver currentUserUuidResolver
    ) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.currentUserUuidResolver = currentUserUuidResolver;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        boolean rememberMe = request.rememberMe() == null || request.rememberMe();
        var result = authService.login(new LoginCommand(
                request.rut(),
                request.password(),
                rememberMe,
                resolveUserAgent(httpRequest)
        ));

        return ResponseEntity.ok()
                .header("Set-Cookie", authCookieService.createAccessCookie(result.accessToken(), result.persistentLogin()).toString())
                .header("Set-Cookie", authCookieService.createRefreshCookie(result.refreshToken(), result.persistentLogin()).toString())
                .body(new LoginResponse(
                        result.role(),
                        result.expiresInSeconds(),
                        new LoginUserResponse(
                                result.user().id(),
                                result.user().name(),
                                result.user().email(),
                                result.user().role()
                        )
                ));
    }

    @PostMapping("/refresh")
    ResponseEntity<Void> refresh(HttpServletRequest request) {
        var result = authService.refresh(
                authCookieService.getRefreshToken(request).orElse(null),
                resolveUserAgent(request)
        );
        return ResponseEntity.noContent()
                .header("Set-Cookie", authCookieService.createAccessCookie(result.accessToken(), result.persistentLogin()).toString())
                .header("Set-Cookie", authCookieService.createRefreshCookie(result.refreshToken(), result.persistentLogin()).toString())
                .build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(
                authCookieService.getAccessToken(request).orElse(null),
                authCookieService.getRefreshToken(request).orElse(null)
        );
        return ResponseEntity.noContent()
                .header("Set-Cookie", authCookieService.expireAccessCookie().toString())
                .header("Set-Cookie", authCookieService.expireRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    CurrentUserResponse getCurrentUser(Authentication authentication) {
        return toCurrentUserResponse(authService.getCurrentUser(resolveCurrentUserUuid(authentication)));
    }

    @PostMapping("/me/bot-token")
    @PreAuthorize("hasAnyRole('COORDINADOR','DIRECTOR')")
    BotAccessTokenResponse issueBotAccessToken(Authentication authentication) {
        var result = authService.issueBotAccessToken(resolveCurrentUserUuid(authentication));
        return new BotAccessTokenResponse(result.token(), result.expiresInSeconds());
    }

    @GetMapping("/me/sessions")
    @PreAuthorize("isAuthenticated()")
    List<CurrentUserSessionResponse> getCurrentUserSessions(Authentication authentication, HttpServletRequest request) {
        return authService.getCurrentUserSessions(
                        resolveCurrentUserUuid(authentication),
                        authCookieService.getRefreshToken(request).orElse(null)
                ).stream()
                .map(session -> new CurrentUserSessionResponse(
                        session.id(),
                        session.current(),
                        session.persistentLogin(),
                        session.userAgent(),
                        session.createdAt(),
                        session.expiresAt()
                ))
                .toList();
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> revokeCurrentUserSession(
            @PathVariable String sessionId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        var result = authService.revokeCurrentUserSession(
                resolveCurrentUserUuid(authentication),
                parseSessionId(sessionId),
                authCookieService.getRefreshToken(request).orElse(null)
        );

        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (result.currentSessionRevoked()) {
            response.header("Set-Cookie", authCookieService.expireAccessCookie().toString());
            response.header("Set-Cookie", authCookieService.expireRefreshCookie().toString());
        }
        return response.build();
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

    private String resolveUserAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }

    private long parseSessionId(String rawSessionId) {
        try {
            return Long.parseLong(rawSessionId);
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_SESSION_ID_INVALID", "Sesion invalida");
        }
    }
}
