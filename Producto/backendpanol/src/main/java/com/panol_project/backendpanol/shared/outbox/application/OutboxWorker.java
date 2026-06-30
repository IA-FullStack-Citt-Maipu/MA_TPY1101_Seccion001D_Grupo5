package com.panol_project.backendpanol.shared.outbox.application;

import com.panol_project.backendpanol.shared.outbox.domain.OutboxEventStatus;
import com.panol_project.backendpanol.shared.outbox.domain.OutboxPublisher;
import com.panol_project.backendpanol.shared.outbox.domain.OutboxRepository;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxWorker {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int MAX_RETRIES = 5;
    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxObservabilityService observabilityService;
    private final UUID outboxSystemUserUuid;

    public OutboxWorker(
            OutboxRepository outboxRepository,
            OutboxPublisher outboxPublisher,
            OutboxObservabilityService observabilityService,
            @Value("${app.outbox.system-user-uuid:}") String outboxSystemUserUuidRaw
    ) {
        this.outboxRepository = outboxRepository;
        this.outboxPublisher = outboxPublisher;
        this.observabilityService = observabilityService;
        this.outboxSystemUserUuid = parseSystemUserUuid(outboxSystemUserUuidRaw);
    }

    @Scheduled(fixedDelayString = "${app.outbox.worker-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        if (outboxSystemUserUuid != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    outboxSystemUserUuid.toString(),
                    null,
                    Collections.emptyList()));
            SecurityContextHolder.setContext(context);
        }

        try {
            outboxRepository.findPending(50).forEach(event -> {
                try {
                    outboxRepository.markProcessing(event.eventId());
                    outboxPublisher.publish(event);
                    outboxRepository.markSent(event.eventId(), OffsetDateTime.now());
                    observabilityService.recordPublishSuccess();
                    LOG.info("outbox_event_published event_id={} aggregate_id={} event_type={}",
                            event.eventId(), event.aggregateId(), event.eventType());
                } catch (Exception ex) {
                    int retries = (event.retryCount() == null ? 0 : event.retryCount()) + 1;
                    OutboxEventStatus status = retries >= MAX_RETRIES ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING;
                    outboxRepository.markRetry(event.eventId(), retries, status);
                    observabilityService.recordPublishFailure();
                    LOG.warn("outbox_event_publish_failed event_id={} aggregate_id={} event_type={} retry_count={} status={}",
                            event.eventId(), event.aggregateId(), event.eventType(), retries, status, ex);
                }
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.metrics-delay-ms:30000}")
    public void reportMetrics() {
        OutboxMetricsSnapshot metrics = observabilityService.snapshot();
        LOG.info("outbox_metrics pending_count={} retry_count={} failed_count={} publish_success_rate={}",
                metrics.pendingCount(),
                metrics.retryCount(),
                metrics.failedCount(),
                metrics.publishSuccessRate());
    }

    private UUID parseSystemUserUuid(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.outbox.system-user-uuid no tiene un UUID valido", ex);
        }
    }
}
