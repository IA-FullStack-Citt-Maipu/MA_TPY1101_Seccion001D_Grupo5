package com.panol_project.backendpanol.modules.email.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.jooq.enums.OutboxStatusEnum;
import com.panol_project.backendpanol.modules.email.domain.EmailOutboxEntry;
import com.panol_project.backendpanol.modules.email.domain.EmailOutboxRepository;
import com.panol_project.backendpanol.modules.email.domain.EmailOutboxStatus;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class EmailOutboxJooqRepository implements EmailOutboxRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public EmailOutboxJooqRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<EmailOutboxEntry> claimPending(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return dsl.fetch("""
                with claimed as (
                    select eo.id
                    from public.email_outbox eo
                    where eo.status = 'PENDING'::public.outbox_status_enum
                    order by eo.occurred_at asc
                    limit ?
                    for update skip locked
                )
                update public.email_outbox eo
                   set status = 'PROCESSING'::public.outbox_status_enum
                  from claimed
                 where eo.id = claimed.id
                returning eo.event_id, eo.recipient_email, eo.email_type, eo.template_data, eo.occurred_at, eo.retry_count
                """, limit)
                .map(record -> new EmailOutboxEntry(
                        record.get("event_id", UUID.class),
                        record.get("recipient_email", String.class),
                        record.get("email_type", String.class),
                        parseTemplateData(record.get("template_data", JSONB.class)),
                        record.get("occurred_at", OffsetDateTime.class),
                        record.get("retry_count", Integer.class)
                ));
    }

    @Override
    public void markSent(UUID eventId, OffsetDateTime processedAt, String providerMessageId) {
        dsl.execute("""
                update public.email_outbox
                   set status = ?::public.outbox_status_enum,
                       processed_at = ?::timestamptz,
                       provider_message_id = ?,
                       error_log = null
                 where event_id = ?
                """, OutboxStatusEnum.SENT.name(), processedAt, providerMessageId, eventId);
    }

    @Override
    public void markRetry(UUID eventId, int retryCount, EmailOutboxStatus status, String errorLog) {
        dsl.execute("""
                update public.email_outbox
                   set status = ?::public.outbox_status_enum,
                       retry_count = ?,
                       error_log = ?,
                       processed_at = case when ?::public.outbox_status_enum = 'FAILED'::public.outbox_status_enum
                                           then now()
                                           else null
                                      end
                 where event_id = ?
                """, status.name(), retryCount, errorLog, status.name(), eventId);
    }

    private Map<String, Object> parseTemplateData(JSONB rawTemplateData) {
        if (rawTemplateData == null || rawTemplateData.data() == null || rawTemplateData.data().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawTemplateData.data(), MAP_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible deserializar template_data desde email_outbox", ex);
        }
    }
}
