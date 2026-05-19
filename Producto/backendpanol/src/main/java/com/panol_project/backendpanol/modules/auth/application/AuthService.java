package com.panol_project.backendpanol.modules.auth.application;

import com.panol_project.backendpanol.modules.auth.application.dto.LoginCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.LoginResult;
import com.panol_project.backendpanol.modules.auth.domain.AuthUser;
import com.panol_project.backendpanol.modules.auth.domain.AuditLogPort;
import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
import com.panol_project.backendpanol.modules.auth.domain.UserAuthPort;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAuthPort userAuthRepository;
    private final TokenRevocationPort tokenRevocationRepository;
    private final JwtEncoder jwtEncoder;
    private final AuditLogPort auditLogPort;
    private final OutboxService outboxService;
    private final int maxFailedAttempts;
    private final int lockMinutes;
    private final int tokenExpirationSeconds;
    private final String jwtIssuer;

    public AuthService(
            UserAuthPort userAuthRepository,
            TokenRevocationPort tokenRevocationRepository,
            JwtEncoder jwtEncoder,
            AuditLogPort auditLogPort,
            OutboxService outboxService,
            @Value("${app.auth.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.auth.lock-minutes:15}") int lockMinutes,
            @Value("${app.auth.jwt.expiration-seconds:3600}") int tokenExpirationSeconds,
            @Value("${app.auth.jwt.issuer:panol-backend}") String jwtIssuer
    ) {
        this.userAuthRepository = userAuthRepository;
        this.tokenRevocationRepository = tokenRevocationRepository;
        this.jwtEncoder = jwtEncoder;
        this.auditLogPort = auditLogPort;
        this.outboxService = outboxService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
        this.tokenExpirationSeconds = tokenExpirationSeconds;
        this.jwtIssuer = jwtIssuer;
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        String rut = normalizeRut(command.rut());
        AuthUser user = userAuthRepository.findAuthUserByRut(rut)
                .orElseThrow(() -> invalidCredentials(rut));

        if (user.blockedUntil() != null && user.blockedUntil().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_TEMPORARILY_BLOCKED", "Credenciales incorrectas");
        }

        boolean validPassword = BCrypt.checkpw(command.password(), user.passwordHash());
        if (!validPassword) {
            int next = user.failedLoginAttempts() + 1;
            OffsetDateTime blockedUntil = next >= maxFailedAttempts
                    ? OffsetDateTime.now().plusMinutes(lockMinutes)
                    : null;
            userAuthRepository.registerFailedAttempt(user.uuid(), next, blockedUntil);
            throw invalidCredentials(rut);
        }

        userAuthRepository.resetLoginAttempts(user.uuid(), OffsetDateTime.now());
        String normalizedRole = normalizeRole(user.roleName());

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(tokenExpirationSeconds);
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .issuedAt(now)
                .expiresAt(exp)
                .subject(user.uuid().toString())
                .claim("role", normalizedRole)
                .claim("jti", jti)
                .build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        auditLogPort.log("user_logged_in", user.uuid(), user.uuid(), Map.of("rut", rut, "role", normalizedRole));
        outboxService.enqueue("user", user.uuid(), "UserLoggedIn", user.uuid(), Map.of("rut", rut, "role", normalizedRole));
        return new LoginResult(token, normalizedRole, tokenExpirationSeconds);
    }

    @Transactional
    public void logout(Jwt jwt) {
        if (jwt == null) {
            return;
        }
        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_JTI_MISSING", "Token invalido");
        }
        String subject = jwt.getSubject();
        UUID userUuid = null;
        if (subject != null && !subject.isBlank()) {
            try {
                userUuid = UUID.fromString(subject);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_SUBJECT_INVALID", "Token invalido");
            }
        }
        if (jwt.getExpiresAt() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRATION_MISSING", "Token invalido");
        }
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC);
        try {
            tokenRevocationRepository.revokeToken(jti, userUuid, expiresAt);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_SUBJECT_INVALID", "Token invalido");
        }
        auditLogPort.log("user_logged_out", null, null, Map.of("jti", jti));
        outboxService.enqueue("auth", userUuid, "UserLoggedOut", userUuid, Map.of("jti", jti));
    }

    private ApiException invalidCredentials(String rut) {
        auditLogPort.log("login_failed", null, null, Map.of("rut", rut));
        outboxService.enqueue("auth", null, "LoginFailed", null, Map.of("rut", rut));
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Credenciales incorrectas");
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null) return "DOCENTE";
        String role = rawRole.trim().toUpperCase();
        if (role.contains("DIRECTOR")) return "DIRECTOR";
        if (role.contains("COORD")) return "COORDINADOR";
        if (role.contains("DOCENTE")) return "DOCENTE";
        return "DOCENTE";
    }

    private String normalizeRut(String rutRaw) {
        String compactRut = rutRaw == null ? "" : rutRaw.replaceAll("[.\\-\\s]", "").trim();
        if (compactRut.length() < 2) {
            return "";
        }

        String rutWithoutVerifier = compactRut.substring(0, compactRut.length() - 1);
        if (rutWithoutVerifier.isBlank() || !rutWithoutVerifier.chars().allMatch(Character::isDigit)) {
            return "";
        }
        return rutWithoutVerifier;
    }
}
