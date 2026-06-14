package com.panol_project.backendpanol.modules.auth.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE_NAME = "panol_access_token";
    public static final String REFRESH_COOKIE_NAME = "panol_refresh_token";

    private final long accessExpirationSeconds;
    private final long refreshExpirationSeconds;
    private final boolean secureCookies;
    private final String sameSite;

    public AuthCookieService(
            @Value("${app.auth.jwt.expiration-seconds:3600}") long accessExpirationSeconds,
            @Value("${app.auth.refresh.expiration-seconds:604800}") long refreshExpirationSeconds,
            @Value("${app.auth.cookies.secure:false}") boolean secureCookies,
            @Value("${app.auth.cookies.same-site:Lax}") String sameSite
    ) {
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
        this.secureCookies = secureCookies;
        this.sameSite = sameSite;
    }

    public ResponseCookie createAccessCookie(String token, boolean persistentLogin) {
        ResponseCookie.ResponseCookieBuilder builder = baseCookie(ACCESS_COOKIE_NAME, token, "/");
        if (persistentLogin) {
            builder.maxAge(Duration.ofSeconds(accessExpirationSeconds));
        }
        return builder.build();
    }

    public ResponseCookie createRefreshCookie(String token, boolean persistentLogin) {
        ResponseCookie.ResponseCookieBuilder builder = baseCookie(REFRESH_COOKIE_NAME, token, "/api/v2/auth");
        if (persistentLogin) {
            builder.maxAge(Duration.ofSeconds(refreshExpirationSeconds));
        }
        return builder.build();
    }

    public ResponseCookie expireAccessCookie() {
        return expiredCookie(ACCESS_COOKIE_NAME, "/");
    }

    public ResponseCookie expireRefreshCookie() {
        return expiredCookie(REFRESH_COOKIE_NAME, "/api/v2/auth");
    }

    public Optional<String> getAccessToken(HttpServletRequest request) {
        return getCookieValue(request, ACCESS_COOKIE_NAME);
    }

    public Optional<String> getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_COOKIE_NAME);
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path(path);
    }

    private ResponseCookie expiredCookie(String name, String path) {
        return baseCookie(name, "", path)
                .maxAge(Duration.ZERO)
                .build();
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
