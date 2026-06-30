package com.panol_project.backendpanol.modules.auth.application;

import com.panol_project.backendpanol.modules.auth.application.dto.LoginCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.ChangeCurrentPasswordCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.AuthenticatedUserSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.BotAccessTokenResult;
import com.panol_project.backendpanol.modules.auth.application.dto.CurrentUserSessionSummary;
import com.panol_project.backendpanol.modules.auth.application.dto.LoginResult;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryRequestCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryResetCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryVerificationResult;
import com.panol_project.backendpanol.modules.auth.application.dto.PasswordRecoveryVerifyCommand;
import com.panol_project.backendpanol.modules.auth.application.dto.RefreshResult;
import com.panol_project.backendpanol.modules.auth.application.dto.RevokeCurrentUserSessionResult;
import com.panol_project.backendpanol.modules.auth.application.dto.UpdateCurrentEmailCommand;
import com.panol_project.backendpanol.modules.auth.domain.AuthUser;
import com.panol_project.backendpanol.modules.auth.domain.AuditLogPort;
import com.panol_project.backendpanol.modules.auth.domain.PasswordRecoveryNotificationPort;
import com.panol_project.backendpanol.modules.auth.domain.PasswordRecoveryRequestRecord;
import com.panol_project.backendpanol.modules.auth.domain.RefreshSession;
import com.panol_project.backendpanol.modules.auth.domain.RefreshSessionPort;
import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
import com.panol_project.backendpanol.modules.auth.domain.UserAuthPort;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final Set<String> BOT_ALLOWED_ROLES = Set.of("COORDINADOR", "DIRECTOR");
    private static final String PASSWORD_RECOVERY_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final UserAuthPort userAuthRepository;
    private final RefreshSessionPort refreshSessionPort;
    private final TokenRevocationPort tokenRevocationRepository;
    private final PasswordRecoveryNotificationPort passwordRecoveryNotificationPort;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuditLogPort auditLogPort;
    private final OutboxService outboxService;
    private final int maxFailedAttempts;
    private final int lockMinutes;
    private final int tokenExpirationSeconds;
    private final int refreshTokenExpirationSeconds;
    private final int temporaryRefreshTokenExpirationSeconds;
    private final int passwordRecoveryCodeLength;
    private final int passwordRecoveryExpirationMinutes;
    private final int passwordRecoveryMaxAttempts;
    private final int passwordRecoveryResendCooldownSeconds;
    private final int passwordRecoveryResetTokenExpirationSeconds;
    private final String jwtIssuer;
    private final int botTokenExpirationSeconds;
    private final String botTokenAudience;

    public AuthService(
            UserAuthPort userAuthRepository,
            RefreshSessionPort refreshSessionPort,
            TokenRevocationPort tokenRevocationRepository,
            PasswordRecoveryNotificationPort passwordRecoveryNotificationPort,
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            AuditLogPort auditLogPort,
            OutboxService outboxService,
            @Value("${app.auth.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.auth.lock-minutes:15}") int lockMinutes,
            @Value("${app.auth.jwt.expiration-seconds:3600}") int tokenExpirationSeconds,
            @Value("${app.auth.refresh.expiration-seconds:604800}") int refreshTokenExpirationSeconds,
            @Value("${app.auth.refresh.temporary-expiration-seconds:86400}") int temporaryRefreshTokenExpirationSeconds,
            @Value("${app.auth.password-recovery.code-length:8}") int passwordRecoveryCodeLength,
            @Value("${app.auth.password-recovery.expiration-minutes:15}") int passwordRecoveryExpirationMinutes,
            @Value("${app.auth.password-recovery.max-attempts:5}") int passwordRecoveryMaxAttempts,
            @Value("${app.auth.password-recovery.resend-cooldown-seconds:120}") int passwordRecoveryResendCooldownSeconds,
            @Value("${app.auth.password-recovery.reset-token-expiration-seconds:600}") int passwordRecoveryResetTokenExpirationSeconds,
            @Value("${app.auth.jwt.issuer:panol-backend}") String jwtIssuer,
            @Value("${app.auth.bot-token.expiration-seconds:300}") int botTokenExpirationSeconds,
            @Value("${app.auth.bot-token.audience:bot-panol}") String botTokenAudience
    ) {
        this.userAuthRepository = userAuthRepository;
        this.refreshSessionPort = refreshSessionPort;
        this.tokenRevocationRepository = tokenRevocationRepository;
        this.passwordRecoveryNotificationPort = passwordRecoveryNotificationPort;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.auditLogPort = auditLogPort;
        this.outboxService = outboxService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
        this.tokenExpirationSeconds = tokenExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
        this.temporaryRefreshTokenExpirationSeconds = temporaryRefreshTokenExpirationSeconds;
        this.passwordRecoveryCodeLength = passwordRecoveryCodeLength;
        this.passwordRecoveryExpirationMinutes = passwordRecoveryExpirationMinutes;
        this.passwordRecoveryMaxAttempts = passwordRecoveryMaxAttempts;
        this.passwordRecoveryResendCooldownSeconds = passwordRecoveryResendCooldownSeconds;
        this.passwordRecoveryResetTokenExpirationSeconds = passwordRecoveryResetTokenExpirationSeconds;
        this.jwtIssuer = jwtIssuer;
        this.botTokenExpirationSeconds = botTokenExpirationSeconds;
        this.botTokenAudience = botTokenAudience == null ? "bot-panol" : botTokenAudience.trim();
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
        AuthenticatedUserSummary authenticatedUser = new AuthenticatedUserSummary(
                user.uuid(),
                user.name(),
                user.email(),
                normalizedRole
        );

        IssuedAccessToken issuedAccessToken = issueAccessToken(user.uuid(), normalizedRole);
        String refreshToken = generateOpaqueToken();
        OffsetDateTime refreshExpiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(resolveRefreshSessionExpirationSeconds(command.rememberMe()));
        refreshSessionPort.createSession(
                user.uuid(),
                hashToken(refreshToken),
                refreshExpiresAt,
                command.deviceInfo(),
                command.rememberMe(),
                issuedAccessToken.jti(),
                issuedAccessToken.expiresAt()
        );

        auditLogPort.log("user_logged_in", user.uuid(), user.uuid(), Map.of("rut", rut, "role", normalizedRole));
        outboxService.enqueue("user", user.uuid(), "UserLoggedIn", user.uuid(), Map.of("rut", rut, "role", normalizedRole));
        return new LoginResult(
                issuedAccessToken.token(),
                refreshToken,
                normalizedRole,
                tokenExpirationSeconds,
                authenticatedUser,
                command.rememberMe()
        );
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken, String deviceInfo) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw refreshSessionInvalid();
        }

        RefreshSession session = refreshSessionPort.findSessionByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(this::refreshSessionInvalid);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (session.expiresAt() == null || !session.expiresAt().isAfter(now)) {
            refreshSessionPort.deleteSessionById(session.id());
            throw refreshSessionInvalid();
        }

        AuthUser user = userAuthRepository.findAuthUserByUuid(session.userUuid())
                .orElseGet(() -> {
                    refreshSessionPort.deleteSessionById(session.id());
                    throw refreshSessionInvalid();
                });

        String normalizedRole = normalizeRole(user.roleName());
        IssuedAccessToken nextAccessToken = issueAccessToken(user.uuid(), normalizedRole);
        String nextRefreshToken = generateOpaqueToken();
        OffsetDateTime nextRefreshExpiresAt = now.plusSeconds(resolveRefreshSessionExpirationSeconds(session.persistentLogin()));

        refreshSessionPort.rotateSession(
                session.id(),
                hashToken(nextRefreshToken),
                nextRefreshExpiresAt,
                deviceInfo,
                session.persistentLogin(),
                nextAccessToken.jti(),
                nextAccessToken.expiresAt()
        );

        return new RefreshResult(nextAccessToken.token(), nextRefreshToken, session.persistentLogin());
    }

    @Transactional
    public void logout(String rawAccessToken, String rawRefreshToken) {
        UUID userUuid = null;
        String jti = null;

        Jwt jwt = decodeValidAccessToken(rawAccessToken);
        if (jwt != null) {
            jti = jwt.getId();
            userUuid = parseUuid(jwt.getSubject());
            if (jti != null && !jti.isBlank() && jwt.getExpiresAt() != null) {
                OffsetDateTime expiresAt = OffsetDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC);
                try {
                    tokenRevocationRepository.revokeToken(jti, userUuid, expiresAt);
                } catch (DataIntegrityViolationException ignored) {
                    // Idempotent logout: una inconsistencia puntual del token no debe romper el cierre de sesion.
                }
            }
        }

        RefreshSession deletedSession = null;
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            deletedSession = refreshSessionPort.deleteSessionByTokenHash(hashToken(rawRefreshToken)).orElse(null);
            if (userUuid == null && deletedSession != null) {
                userUuid = deletedSession.userUuid();
            }
        }

        if (jti != null || deletedSession != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (jti != null && !jti.isBlank()) {
                payload.put("jti", jti);
            }
            if (deletedSession != null) {
                payload.put("session_id", deletedSession.id());
            }
            auditLogPort.log("user_logged_out", userUuid, userUuid, Map.copyOf(payload));
            outboxService.enqueue("auth", userUuid, "UserLoggedOut", userUuid, Map.copyOf(payload));
        }
    }

    @Transactional(readOnly = true)
    public List<CurrentUserSessionSummary> getCurrentUserSessions(UUID userUuid, String rawCurrentRefreshToken) {
        requireUserByUuid(userUuid);
        String currentRefreshTokenHash = hashTokenOrNull(rawCurrentRefreshToken);

        return refreshSessionPort.findSessionsByUserUuid(userUuid).stream()
                .map(session -> new CurrentUserSessionSummary(
                        String.valueOf(session.id()),
                        currentRefreshTokenHash != null && currentRefreshTokenHash.equals(session.refreshTokenHash()),
                        session.persistentLogin(),
                        session.userAgent(),
                        session.createdAt(),
                        session.currentAccessExpiresAt(),
                        session.expiresAt()
                ))
                .toList();
    }

    @Transactional
    public RevokeCurrentUserSessionResult revokeCurrentUserSession(UUID userUuid, long sessionId, String rawCurrentRefreshToken) {
        requireUserByUuid(userUuid);

        RefreshSession session = refreshSessionPort.findSessionByIdAndUserUuid(sessionId, userUuid)
                .orElseThrow(this::sessionNotFound);

        boolean currentSession = matchesCurrentRefreshSession(session, rawCurrentRefreshToken);
        revokeStoredAccessToken(session);
        refreshSessionPort.deleteSessionById(session.id());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", session.id());
        payload.put("current", currentSession);
        if (session.currentAccessJti() != null && !session.currentAccessJti().isBlank()) {
            payload.put("jti", session.currentAccessJti());
        }

        auditLogPort.log("user_session_revoked", userUuid, userUuid, Map.copyOf(payload));
        outboxService.enqueue("auth", userUuid, "UserSessionRevoked", userUuid, Map.copyOf(payload));

        return new RevokeCurrentUserSessionResult(currentSession);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserSummary getCurrentUser(UUID userUuid) {
        AuthUser user = requireUserByUuid(userUuid);
        return toAuthenticatedUserSummary(user);
    }

    @Transactional(readOnly = true)
    public BotAccessTokenResult issueBotAccessToken(UUID userUuid) {
        AuthUser user = requireUserByUuid(userUuid);
        String normalizedRole = normalizeRole(user.roleName());
        if (!BOT_ALLOWED_ROLES.contains(normalizedRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_BOT_TOKEN_FORBIDDEN", "No autorizado para usar el asistente");
        }

        IssuedAccessToken botToken = issueAccessToken(
                user.uuid(),
                normalizedRole,
                botTokenExpirationSeconds,
                botTokenAudience,
                "bot-panol"
        );

        return new BotAccessTokenResult(botToken.token(), botTokenExpirationSeconds);
    }

    @Transactional
    public AuthenticatedUserSummary updateCurrentUserEmail(UUID userUuid, UpdateCurrentEmailCommand command) {
        AuthUser user = requireUserByUuid(userUuid);
        String normalizedEmail = normalizeEmail(command.email());

        if (user.email() != null && normalizedEmail.equalsIgnoreCase(user.email())) {
            return toAuthenticatedUserSummary(user);
        }

        if (userAuthRepository.existsOtherUserWithEmail(normalizedEmail, userUuid)) {
            throw new ApiException(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_IN_USE", "El correo ya esta en uso");
        }

        userAuthRepository.updateEmail(userUuid, normalizedEmail);
        auditLogPort.log("user_email_changed", userUuid, userUuid, Map.of("email", normalizedEmail));
        outboxService.enqueue("user", userUuid, "UserEmailChanged", userUuid, Map.of("email", normalizedEmail));
        return new AuthenticatedUserSummary(
                user.uuid(),
                user.name(),
                normalizedEmail,
                normalizeRole(user.roleName())
        );
    }

    @Transactional
    public void updateCurrentUserPassword(UUID userUuid, ChangeCurrentPasswordCommand command) {
        AuthUser user = requireUserByUuid(userUuid);
        String currentPassword = command.currentPassword();
        String newPassword = command.newPassword();

        if (currentPassword == null || currentPassword.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_CURRENT_PASSWORD_REQUIRED", "Debes ingresar tu contrasena actual");
        }
        if (!BCrypt.checkpw(currentPassword, user.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_CURRENT_PASSWORD_INVALID", "La contrasena actual no coincide");
        }
        validateNewPassword(newPassword, user.passwordHash());

        userAuthRepository.updatePasswordHash(userUuid, BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        auditLogPort.log("user_password_changed", userUuid, userUuid, Map.of("source", "self_service"));
        outboxService.enqueue("user", userUuid, "UserPasswordChanged", userUuid, Map.of("source", "self_service"));
    }

    @Transactional
    public void requestPasswordRecovery(PasswordRecoveryRequestCommand command) {
        if (!passwordRecoveryNotificationPort.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PASSWORD_RECOVERY_UNAVAILABLE", "La recuperacion de contrasena no esta disponible en este momento");
        }

        String rut = normalizeRut(command.rut());
        if (rut.isBlank()) {
            return;
        }

        AuthUser user = userAuthRepository.findAuthUserByRut(rut).orElse(null);
        if (user == null || user.email() == null || user.email().trim().isBlank()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PasswordRecoveryRequestRecord latestRequest = userAuthRepository.findLatestPasswordRecoveryRequestByUserUuid(user.uuid()).orElse(null);
        if (latestRequest != null
                && latestRequest.consumedAt() == null
                && latestRequest.lastSentAt() != null
                && latestRequest.lastSentAt().plusSeconds(passwordRecoveryResendCooldownSeconds).isAfter(now)) {
            return;
        }

        userAuthRepository.invalidatePasswordRecoveryRequests(user.uuid(), now);

        String verificationCode = generatePasswordRecoveryCode();
        userAuthRepository.createPasswordRecoveryRequest(
                user.uuid(),
                hashToken(verificationCode),
                now.plusMinutes(passwordRecoveryExpirationMinutes),
                now
        );
        passwordRecoveryNotificationPort.enqueuePasswordRecoveryEmail(
                user.name(),
                user.email(),
                rut,
                verificationCode,
                passwordRecoveryExpirationMinutes
        );
        auditLogPort.log("user_password_recovery_requested", user.uuid(), user.uuid(), Map.of("rut", rut));
        outboxService.enqueue("auth", user.uuid(), "UserPasswordRecoveryRequested", user.uuid(), Map.of("rut", rut));
    }

    @Transactional
    public PasswordRecoveryVerificationResult verifyPasswordRecoveryCode(PasswordRecoveryVerifyCommand command) {
        String rut = normalizeRut(command.rut());
        String verificationCode = normalizePasswordRecoveryCode(command.code());
        if (verificationCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_RECOVERY_CODE_REQUIRED", "Debes ingresar el codigo de verificacion");
        }

        AuthUser user = userAuthRepository.findAuthUserByRut(rut).orElseThrow(this::passwordRecoveryCodeInvalid);
        PasswordRecoveryRequestRecord requestRecord = userAuthRepository.findLatestPasswordRecoveryRequestByUserUuid(user.uuid())
                .orElseThrow(this::passwordRecoveryCodeInvalid);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (requestRecord.consumedAt() != null) {
            throw passwordRecoveryCodeInvalid();
        }
        if (requestRecord.expiresAt() == null || !requestRecord.expiresAt().isAfter(now)) {
            userAuthRepository.consumePasswordRecoveryRequest(requestRecord.id(), now);
            throw new ApiException(HttpStatus.GONE, "AUTH_PASSWORD_RECOVERY_CODE_EXPIRED", "El codigo de verificacion expiro");
        }

        if (!hashToken(verificationCode).equals(requestRecord.codeHash())) {
            int nextAttemptCount = requestRecord.attemptCount() + 1;
            userAuthRepository.incrementPasswordRecoveryAttempt(requestRecord.id(), nextAttemptCount, now);
            if (nextAttemptCount >= passwordRecoveryMaxAttempts) {
                userAuthRepository.consumePasswordRecoveryRequest(requestRecord.id(), now);
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AUTH_PASSWORD_RECOVERY_CODE_ATTEMPTS_EXCEEDED",
                        "Superaste el maximo de intentos permitidos para este codigo"
                );
            }
            throw passwordRecoveryCodeInvalid();
        }

        String resetToken = generateOpaqueToken();
        userAuthRepository.verifyPasswordRecoveryRequest(requestRecord.id(), hashToken(resetToken), now, now);
        auditLogPort.log("user_password_recovery_verified", user.uuid(), user.uuid(), Map.of("rut", rut));
        outboxService.enqueue("auth", user.uuid(), "UserPasswordRecoveryVerified", user.uuid(), Map.of("rut", rut));
        return new PasswordRecoveryVerificationResult(resetToken, passwordRecoveryResetTokenExpirationSeconds);
    }

    @Transactional
    public void resetPasswordFromRecovery(PasswordRecoveryResetCommand command) {
        String resetToken = command.resetToken() == null ? "" : command.resetToken().trim();
        if (resetToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_RECOVERY_TOKEN_INVALID", "El token de recuperacion es invalido");
        }

        PasswordRecoveryRequestRecord requestRecord = userAuthRepository.findPasswordRecoveryRequestByResetTokenHash(hashToken(resetToken))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_RECOVERY_TOKEN_INVALID", "El token de recuperacion es invalido"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (requestRecord.consumedAt() != null || requestRecord.verifiedAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_RECOVERY_TOKEN_INVALID", "El token de recuperacion es invalido");
        }
        if (requestRecord.expiresAt() == null || !requestRecord.expiresAt().isAfter(now)) {
            userAuthRepository.consumePasswordRecoveryRequest(requestRecord.id(), now);
            throw new ApiException(HttpStatus.GONE, "AUTH_PASSWORD_RECOVERY_TOKEN_EXPIRED", "El token de recuperacion expiro");
        }

        validateNewPassword(command.newPassword(), requestRecord.userPasswordHash());

        userAuthRepository.updatePasswordHash(requestRecord.userUuid(), BCrypt.hashpw(command.newPassword(), BCrypt.gensalt()));
        revokeAllUserSessions(requestRecord.userUuid());
        userAuthRepository.consumePasswordRecoveryRequest(requestRecord.id(), now);
        auditLogPort.log("user_password_changed", requestRecord.userUuid(), requestRecord.userUuid(), Map.of("source", "password_recovery"));
        outboxService.enqueue("user", requestRecord.userUuid(), "UserPasswordChanged", requestRecord.userUuid(), Map.of("source", "password_recovery"));
    }

    private ApiException invalidCredentials(String rut) {
        auditLogPort.log("login_failed", null, null, Map.of("rut", rut));
        outboxService.enqueue("auth", null, "LoginFailed", null, Map.of("rut", rut));
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Credenciales incorrectas");
    }

    private ApiException refreshSessionInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_SESSION_INVALID", "Sesion expirada");
    }

    private ApiException passwordRecoveryCodeInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_RECOVERY_CODE_INVALID", "El codigo de verificacion no es valido");
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
        String compactRut = rutRaw == null ? "" : rutRaw.replaceAll("[.\\-\\s]", "").trim().toUpperCase();
        if (compactRut.length() < 2) {
            return "";
        }

        if (!compactRut.chars().allMatch(ch -> Character.isDigit(ch) || ch == 'K')) {
            return "";
        }

        String body = compactRut.substring(0, compactRut.length() - 1);
        char verifier = compactRut.charAt(compactRut.length() - 1);
        if (!body.isBlank() && body.chars().allMatch(Character::isDigit) && (Character.isDigit(verifier) || verifier == 'K')) {
            return compactRut;
        }

        return compactRut.chars().allMatch(Character::isDigit) ? compactRut : "";
    }

    private String normalizeEmail(String emailRaw) {
        if (emailRaw == null || emailRaw.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_REQUIRED", "El correo es obligatorio");
        }
        return emailRaw.trim().toLowerCase();
    }

    private String normalizePasswordRecoveryCode(String rawCode) {
        if (rawCode == null || rawCode.trim().isBlank()) {
            return "";
        }
        String normalized = rawCode.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
        if (normalized.length() != passwordRecoveryCodeLength) {
            return "";
        }
        return normalized;
    }

    private String generatePasswordRecoveryCode() {
        StringBuilder builder = new StringBuilder(passwordRecoveryCodeLength);
        for (int i = 0; i < passwordRecoveryCodeLength; i++) {
            int index = SECURE_RANDOM.nextInt(PASSWORD_RECOVERY_CODE_ALPHABET.length());
            builder.append(PASSWORD_RECOVERY_CODE_ALPHABET.charAt(index));
        }
        return builder.toString();
    }

    private void validateNewPassword(String newPassword, String currentPasswordHash) {
        if (newPassword == null || newPassword.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_NEW_PASSWORD_REQUIRED", "Debes ingresar una nueva contrasena");
        }
        if (newPassword.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_NEW_PASSWORD_TOO_SHORT", "La nueva contrasena debe tener al menos 8 caracteres");
        }
        if (currentPasswordHash != null && BCrypt.checkpw(newPassword, currentPasswordHash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_REUSE_NOT_ALLOWED", "La nueva contrasena debe ser distinta a la actual");
        }
    }

    private IssuedAccessToken issueAccessToken(UUID userUuid, String normalizedRole) {
        return issueAccessToken(userUuid, normalizedRole, tokenExpirationSeconds, null, null);
    }

    private IssuedAccessToken issueAccessToken(
            UUID userUuid,
            String normalizedRole,
            int expirationSeconds,
            String audience,
            String tokenUse
    ) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .issuedAt(now)
                .expiresAt(exp)
                .subject(userUuid.toString())
                .id(jti)
                .claim("role", normalizedRole);

        if (audience != null && !audience.isBlank()) {
            claimsBuilder.audience(List.of(audience));
        }
        if (tokenUse != null && !tokenUse.isBlank()) {
            claimsBuilder.claim("token_use", tokenUse);
        }

        JwtClaimsSet claims = claimsBuilder.build();

        String token = jwtEncoder.encode(
                        JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new IssuedAccessToken(token, jti, OffsetDateTime.ofInstant(exp, ZoneOffset.UTC));
    }

    private Jwt decodeValidAccessToken(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return null;
        }
        try {
            return jwtDecoder.decode(rawAccessToken);
        } catch (JwtException ex) {
            return null;
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String generateOpaqueToken() {
        byte[] value = new byte[32];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private boolean matchesCurrentRefreshSession(RefreshSession session, String rawCurrentRefreshToken) {
        String currentRefreshTokenHash = hashTokenOrNull(rawCurrentRefreshToken);
        return currentRefreshTokenHash != null && currentRefreshTokenHash.equals(session.refreshTokenHash());
    }

    private void revokeStoredAccessToken(RefreshSession session) {
        if (session.currentAccessJti() == null || session.currentAccessJti().isBlank()) {
            return;
        }

        OffsetDateTime accessExpiresAt = session.currentAccessExpiresAt() != null
                ? session.currentAccessExpiresAt()
                : OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(tokenExpirationSeconds);

        try {
            tokenRevocationRepository.revokeToken(session.currentAccessJti(), session.userUuid(), accessExpiresAt);
        } catch (DataIntegrityViolationException ignored) {
            // Revocar una sesion remota debe ser idempotente si el jti ya fue invalidado.
        }
    }

    private int resolveRefreshSessionExpirationSeconds(boolean persistentLogin) {
        return persistentLogin ? refreshTokenExpirationSeconds : temporaryRefreshTokenExpirationSeconds;
    }

    private void revokeAllUserSessions(UUID userUuid) {
        List<RefreshSession> deletedSessions = refreshSessionPort.deleteSessionsByUserUuid(userUuid);
        for (RefreshSession session : deletedSessions) {
            revokeStoredAccessToken(session);
        }
    }

    private String hashToken(String rawToken) {
        try {
            return HEX_FORMAT.formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private String hashTokenOrNull(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return hashToken(rawToken);
    }

    private AuthUser requireUserByUuid(UUID userUuid) {
        if (userUuid == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida");
        }
        return userAuthRepository.findAuthUserByUuid(userUuid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUTH_USER_NOT_FOUND", "Usuario no encontrado"));
    }

    private ApiException sessionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "AUTH_SESSION_NOT_FOUND", "Sesion no encontrada");
    }

    private AuthenticatedUserSummary toAuthenticatedUserSummary(AuthUser user) {
        return new AuthenticatedUserSummary(
                user.uuid(),
                user.name(),
                user.email(),
                normalizeRole(user.roleName())
        );
    }

    private record IssuedAccessToken(
            String token,
            String jti,
            OffsetDateTime expiresAt
    ) {
    }
}
