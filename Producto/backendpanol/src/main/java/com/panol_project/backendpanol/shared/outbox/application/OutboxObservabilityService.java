package com.panol_project.backendpanol.shared.outbox.application;

import com.panol_project.backendpanol.shared.outbox.domain.OutboxEventStatus;
import com.panol_project.backendpanol.shared.outbox.domain.OutboxRepository;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class OutboxObservabilityService {

    private final OutboxRepository outboxRepository;
    private final AtomicLong publishSuccess = new AtomicLong(0);
    private final AtomicLong publishFailures = new AtomicLong(0);

    public OutboxObservabilityService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void recordPublishSuccess() {
        publishSuccess.incrementAndGet();
    }

    public void recordPublishFailure() {
        publishFailures.incrementAndGet();
    }

    public OutboxMetricsSnapshot snapshot() {
        long success = publishSuccess.get();
        long failures = publishFailures.get();
        double rate = (success + failures) == 0 ? 1.0 : (double) success / (success + failures);
        return new OutboxMetricsSnapshot(
                outboxRepository.countByStatus(OutboxEventStatus.PENDING),
                outboxRepository.countByStatus(OutboxEventStatus.FAILED),
                outboxRepository.countWithRetries(),
                rate
        );
    }
}
