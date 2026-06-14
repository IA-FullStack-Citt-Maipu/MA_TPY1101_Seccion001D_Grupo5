package com.panol_project.backendpanol.shared.security;

import com.panol_project.backendpanol.modules.auth.api.AuthCookieService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

@Component
public class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> COOKIE_FREE_ENDPOINTS = Set.of(
            "/api/v2/auth/login",
            "/api/v2/auth/logout",
            "/api/v2/auth/refresh"
    );

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final AuthCookieService authCookieService;

    public CookieOrHeaderBearerTokenResolver(AuthCookieService authCookieService) {
        this.authCookieService = authCookieService;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (COOKIE_FREE_ENDPOINTS.contains(path)) {
            return null;
        }

        String headerToken = headerResolver.resolve(request);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }

        return authCookieService.getAccessToken(request).orElse(null);
    }
}
