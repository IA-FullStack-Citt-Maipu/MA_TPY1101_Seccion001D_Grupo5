package com.panol_project.backendpanol.modules.notification.application;

import com.panol_project.backendpanol.modules.notification.domain.NotificationRepository;
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
        value = "app.notification.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationCleanupWorker {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationCleanupWorker.class);
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_RETENTION_DAYS = 15;

    private final NotificationRepository notificationRepository;
    private final int batchSize;
    private final int retentionDays;

    public NotificationCleanupWorker(
            NotificationRepository notificationRepository,
            @Value("${app.notification.cleanup.batch-size:500}") int batchSize,
            @Value("${app.notification.cleanup.retention-days:15}") int retentionDays
    ) {
        this.notificationRepository = notificationRepository;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        this.retentionDays = retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
    }

    @Scheduled(
            initialDelayString = "${app.notification.cleanup.initial-delay-ms:300000}",
            fixedDelayString = "${app.notification.cleanup.delay-ms:86400000}"
    )
    @Transactional
    public void purgeReadNotifications() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        int totalDeleted = 0;

        try {
            int deleted;
            do {
                deleted = notificationRepository.deleteReadOlderThan(cutoff, batchSize);
                totalDeleted += deleted;
            } while (deleted == batchSize);

            if (totalDeleted > 0) {
                LOG.info("notification_cleanup deleted={} retention_days={} batch_size={}",
                        totalDeleted, retentionDays, batchSize);
            }
        } catch (RuntimeException ex) {
            LOG.error("notification_cleanup_failed retention_days={} batch_size={}",
                    retentionDays, batchSize, ex);
        }
    }
}
