package com.panol_project.backendpanol.modules.email.application;

import com.panol_project.backendpanol.modules.email.domain.EmailDeliveryException;
import com.panol_project.backendpanol.modules.email.domain.EmailOutboxEntry;
import com.panol_project.backendpanol.modules.email.domain.EmailOutboxRepository;
import com.panol_project.backendpanol.modules.email.domain.EmailOutboxStatus;
import com.panol_project.backendpanol.modules.email.domain.RenderedEmail;
import com.panol_project.backendpanol.modules.email.infrastructure.ResendEmailClient;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailOutboxWorker {

    private static final Logger LOG = LoggerFactory.getLogger(EmailOutboxWorker.class);

    private final EmailOutboxRepository repository;
    private final EmailTemplateRenderer renderer;
    private final ResendEmailClient resendEmailClient;
    private final EmailProperties properties;

    public EmailOutboxWorker(
            EmailOutboxRepository repository,
            EmailTemplateRenderer renderer,
            ResendEmailClient resendEmailClient,
            EmailProperties properties
    ) {
        this.repository = repository;
        this.renderer = renderer;
        this.resendEmailClient = resendEmailClient;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.email.worker-delay-ms:5000}")
    public void sendPendingEmails() {
        if (!properties.isEnabled()) {
            return;
        }

        List<EmailOutboxEntry> entries = repository.claimPending(properties.getBatchSize());
        for (EmailOutboxEntry entry : entries) {
            try {
                RenderedEmail renderedEmail = renderer.render(entry.emailType(), entry.templateData());
                String providerMessageId = resendEmailClient.send(
                        entry.recipientEmail(),
                        renderedEmail.subject(),
                        renderedEmail.html()
                );
                repository.markSent(entry.eventId(), OffsetDateTime.now(), providerMessageId);
                LOG.info("email_outbox_sent event_id={} email_type={} recipient={}",
                        entry.eventId(), entry.emailType(), entry.recipientEmail());
            } catch (EmailDeliveryException ex) {
                handleFailure(entry, ex);
            } catch (Exception ex) {
                handleFailure(entry, new EmailDeliveryException("Fallo inesperado al enviar email", ex));
            }
        }
    }

    private void handleFailure(EmailOutboxEntry entry, EmailDeliveryException exception) {
        int retries = (entry.retryCount() == null ? 0 : entry.retryCount()) + 1;
        EmailOutboxStatus status = retries >= properties.getMaxRetries()
                ? EmailOutboxStatus.FAILED
                : EmailOutboxStatus.PENDING;
        repository.markRetry(entry.eventId(), retries, status, trimErrorLog(exception));
        LOG.warn("email_outbox_failed event_id={} email_type={} recipient={} retry_count={} status={}",
                entry.eventId(), entry.emailType(), entry.recipientEmail(), retries, status, exception);
    }

    private String trimErrorLog(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.length() > 4000 ? message.substring(0, 4000) : message;
    }
}
