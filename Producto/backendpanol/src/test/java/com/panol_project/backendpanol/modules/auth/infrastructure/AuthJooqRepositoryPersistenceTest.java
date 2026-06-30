package com.panol_project.backendpanol.modules.auth.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Role.ROLE;
import static com.panol_project.backendpanol.jooq.tables.TokenRevocation.TOKEN_REVOCATION;
import static com.panol_project.backendpanol.jooq.tables.User.USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.outbox.worker-delay-ms=600000",
        "app.outbox.metrics-delay-ms=600000",
        "app.auth.token-revocation-cleanup.enabled=false",
        "spring.datasource.url=${JOOQ_DB_URL:jdbc:postgresql://127.0.0.1:5432/panol_ci}",
        "spring.datasource.username=${JOOQ_DB_USER:panol_ci}",
        "spring.datasource.password=${JOOQ_DB_PASSWORD:panol_ci}"
})
@ActiveProfiles("docker")
class AuthJooqRepositoryPersistenceTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private AuthJooqRepository authJooqRepository;

    private final List<String> revocationJtisToCleanup = new ArrayList<>();
    private final List<UUID> userUuidsToCleanup = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cleanupRevocations();
        cleanupUsers();
    }

    @Test
    void isRevokedDebeRetornarFalseConJtiNuloOVacio() {
        assertFalse(authJooqRepository.isRevoked(null));
        assertFalse(authJooqRepository.isRevoked(""));
        assertFalse(authJooqRepository.isRevoked("   "));
    }

    @Test
    void isRevokedDebeConsultarExistenciaRealEnTokenRevocation() {
        UUID userUuid = insertUser("docente", "Docente Revocation");
        insertRevocation(userUuid, "revoked-jti-" + UUID.randomUUID(), OffsetDateTime.now().plusHours(1));

        assertTrue(authJooqRepository.isRevoked(revocationJtisToCleanup.getLast()));
        assertFalse(authJooqRepository.isRevoked("missing-jti-" + UUID.randomUUID()));
    }

    @Test
    void deleteExpiredRevocationsDebeBorrarSoloExpiradosYRespetarLimit() {
        UUID userUuid = insertUser("coordinador", "Coordinador Cleanup");
        OffsetDateTime now = OffsetDateTime.now().withNano(0);
        String oldestExpiredJti = "expired-old-" + UUID.randomUUID();
        String newestExpiredJti = "expired-new-" + UUID.randomUUID();
        String activeJti = "active-" + UUID.randomUUID();

        insertRevocation(userUuid, oldestExpiredJti, now.minusDays(2));
        insertRevocation(userUuid, newestExpiredJti, now.minusHours(2));
        insertRevocation(userUuid, activeJti, now.plusDays(2));

        int deletedFirstBatch = authJooqRepository.deleteExpiredRevocations(now, 1);

        assertEquals(1, deletedFirstBatch);
        assertFalse(authJooqRepository.isRevoked(oldestExpiredJti));
        assertTrue(authJooqRepository.isRevoked(newestExpiredJti));
        assertTrue(authJooqRepository.isRevoked(activeJti));

        int deletedSecondBatch = authJooqRepository.deleteExpiredRevocations(now, 10);

        assertEquals(1, deletedSecondBatch);
        assertFalse(authJooqRepository.isRevoked(newestExpiredJti));
        assertTrue(authJooqRepository.isRevoked(activeJti));
    }

    private UUID insertUser(String roleName, String displayName) {
        UUID userUuid = UUID.randomUUID();
        String suffix = userUuid.toString().substring(0, 8);
        Long roleId = dsl.select(ROLE.ID)
                .from(ROLE)
                .where(ROLE.NAME.eq(roleName))
                .fetchOne(ROLE.ID);

        dsl.insertInto(USER)
                .set(USER.UUID, userUuid)
                .set(USER.ROLE_ID, roleId)
                .set(USER.NAME, displayName)
                .set(USER.RUT, "rut-auth-" + suffix)
                .set(USER.EMAIL, "auth-" + suffix + "@panol.test")
                .set(USER.PASSWORD_HASH, "$2a$10$falsa_pero_valida_para_not_null")
                .set(USER.ACTIVE, true)
                .execute();

        userUuidsToCleanup.add(userUuid);
        return userUuid;
    }

    private void insertRevocation(UUID userUuid, String jti, OffsetDateTime expiresAt) {
        Long userId = dsl.select(USER.ID)
                .from(USER)
                .where(USER.UUID.eq(userUuid))
                .fetchOne(USER.ID);

        dsl.insertInto(TOKEN_REVOCATION)
                .set(TOKEN_REVOCATION.USER_ID, userId)
                .set(TOKEN_REVOCATION.JTI, jti)
                .set(TOKEN_REVOCATION.EXPIRES_AT, expiresAt)
                .execute();

        revocationJtisToCleanup.add(jti);
    }

    private void cleanupRevocations() {
        if (revocationJtisToCleanup.isEmpty()) {
            return;
        }

        dsl.deleteFrom(TOKEN_REVOCATION)
                .where(TOKEN_REVOCATION.JTI.in(revocationJtisToCleanup))
                .execute();
        revocationJtisToCleanup.clear();
    }

    private void cleanupUsers() {
        if (userUuidsToCleanup.isEmpty()) {
            return;
        }

        dsl.deleteFrom(USER)
                .where(USER.UUID.in(userUuidsToCleanup))
                .execute();
        userUuidsToCleanup.clear();
    }

    @TestConfiguration
    static class NoOpSchedulingConfig {

        @Bean(name = "taskScheduler")
        TaskScheduler taskScheduler() {
            return new NoOpTaskScheduler();
        }
    }

    private static final class NoOpTaskScheduler implements TaskScheduler {

        @Override
        public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, java.time.Instant startTime) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Instant startTime, java.time.Duration period) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Duration period) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Instant startTime, java.time.Duration delay) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Duration delay) {
            return null;
        }
    }
}
