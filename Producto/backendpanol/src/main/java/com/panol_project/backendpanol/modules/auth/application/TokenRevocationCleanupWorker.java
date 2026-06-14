package com.panol_project.backendpanol.modules.auth.application;

import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
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
        value = "app.auth.token-revocation-cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TokenRevocationCleanupWorker {

    private static final Logger LOG = LoggerFactory.getLogger(TokenRevocationCleanupWorker.class);
    private static final int DEFAULT_BATCH_SIZE = 500;

    private final TokenRevocationPort tokenRevocationPort;
    private final int batchSize;

    public TokenRevocationCleanupWorker(
            TokenRevocationPort tokenRevocationPort,
            @Value("${app.auth.token-revocation-cleanup.batch-size:500}") int batchSize
    ) {
        this.tokenRevocationPort = tokenRevocationPort;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
    }

    @Scheduled(
            initialDelayString = "${app.auth.token-revocation-cleanup.initial-delay-ms:300000}",
            fixedDelayString = "${app.auth.token-revocation-cleanup.delay-ms:1800000}"
    )
    @Transactional
    public void purgeExpiredRevocations() {
        OffsetDateTime now = OffsetDateTime.now();
        try {
            int deleted = tokenRevocationPort.deleteExpiredRevocations(now, batchSize);
            if (deleted > 0) {
                LOG.info("token_revocation_cleanup deleted={} batch_size={}", deleted, batchSize);
            }
        } catch (RuntimeException ex) {
            LOG.error("token_revocation_cleanup_failed batch_size={}", batchSize, ex);
        }
    }
}
