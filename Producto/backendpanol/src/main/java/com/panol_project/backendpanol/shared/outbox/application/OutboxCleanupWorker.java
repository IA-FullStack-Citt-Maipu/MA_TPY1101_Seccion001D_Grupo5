package com.panol_project.backendpanol.shared.outbox.application;

import com.panol_project.backendpanol.shared.outbox.domain.OutboxRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        value = "app.outbox.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxCleanupWorker {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxCleanupWorker.class);
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final OutboxRepository outboxRepository;
    private final int batchSize;
    private final int retentionDays;

    public OutboxCleanupWorker(
            OutboxRepository outboxRepository,
            @Value("${app.outbox.cleanup.batch-size:500}") int batchSize,
            @Value("${app.outbox.cleanup.retention-days:30}") int retentionDays
    ) {
        this.outboxRepository = outboxRepository;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        this.retentionDays = retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
    }

    @Scheduled(
            initialDelayString = "${app.outbox.cleanup.initial-delay-ms:300000}",
            fixedDelayString = "${app.outbox.cleanup.delay-ms:86400000}"
    )
    @Transactional
    public void purgeSentEvents() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        int totalDeleted = 0;

        try {
            int deleted;
            do {
                deleted = outboxRepository.deleteSentOlderThan(cutoff, batchSize);
                totalDeleted += deleted;
            } while (deleted == batchSize);

            if (totalDeleted > 0) {
                LOG.info("outbox_cleanup deleted={} retention_days={} batch_size={}",
                        totalDeleted, retentionDays, batchSize);
            }
        } catch (RuntimeException ex) {
            LOG.error("outbox_cleanup_failed retention_days={} batch_size={}",
                    retentionDays, batchSize, ex);
        }
    }
}
