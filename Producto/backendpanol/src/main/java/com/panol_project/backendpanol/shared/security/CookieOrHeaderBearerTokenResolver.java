package com.panol_project.backendpanol.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
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

        return getCookieValue(request, AuthCookieNames.ACCESS_COOKIE_NAME).orElse(null);
    }

    private Optional<String> getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
