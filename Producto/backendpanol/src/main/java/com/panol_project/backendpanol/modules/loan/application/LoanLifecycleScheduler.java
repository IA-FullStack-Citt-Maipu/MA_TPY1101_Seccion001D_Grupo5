package com.panol_project.backendpanol.modules.loan.application;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "app.loan.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
public class LoanLifecycleScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(LoanLifecycleScheduler.class);

    private final GestionPrestamoUseCase gestionPrestamoUseCase;
    private final UUID systemUserUuid;
    private final int expireGraceMinutes;

    public LoanLifecycleScheduler(
            GestionPrestamoUseCase gestionPrestamoUseCase,
            @Value("${app.loan.lifecycle.system-user-uuid:${app.outbox.system-user-uuid:}}") String systemUserUuidRaw,
            @Value("${app.loan.lifecycle.expire.grace-minutes:120}") int expireGraceMinutes
    ) {
        this.gestionPrestamoUseCase = gestionPrestamoUseCase;
        this.systemUserUuid = parseSystemUserUuid(systemUserUuidRaw);
        this.expireGraceMinutes = Math.max(0, expireGraceMinutes);
    }

    @Scheduled(
            initialDelayString = "${app.loan.lifecycle.mark-overdue-initial-delay-ms:60000}",
            fixedDelayString = "${app.loan.lifecycle.mark-overdue-delay-ms:60000}"
    )
    public void markOverdueLoans() {
        if (systemUserUuid == null) {
            return;
        }
        try {
            int affected = gestionPrestamoUseCase.marcarPrestamosOverdue(systemUserUuid, OffsetDateTime.now());
            if (affected > 0) {
                LOG.info("loan_lifecycle_mark_overdue affected={}", affected);
            }
        } catch (RuntimeException ex) {
            LOG.error("loan_lifecycle_mark_overdue_failed", ex);
        }
    }

    @Scheduled(
            initialDelayString = "${app.loan.lifecycle.expire-pending-initial-delay-ms:60000}",
            fixedDelayString = "${app.loan.lifecycle.expire-pending-delay-ms:60000}"
    )
    public void expirePendingLoans() {
        if (systemUserUuid == null) {
            return;
        }
        try {
            int affected = gestionPrestamoUseCase.expirarPrestamosPendientes(
                    systemUserUuid,
                    OffsetDateTime.now(),
                    expireGraceMinutes
            );
            if (affected > 0) {
                LOG.info("loan_lifecycle_expire_pending affected={} grace_minutes={}", affected, expireGraceMinutes);
            }
        } catch (RuntimeException ex) {
            LOG.error("loan_lifecycle_expire_pending_failed", ex);
        }
    }

    private UUID parseSystemUserUuid(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.loan.lifecycle.system-user-uuid no tiene un UUID valido", ex);
        }
    }
}
