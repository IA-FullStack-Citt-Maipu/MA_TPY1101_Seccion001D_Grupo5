package com.panol_project.backendpanol.modules.auth.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.modules.auth.domain.AuditLogPort;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogJooqAdapter implements AuditLogPort {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogJooqAdapter.class);

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public AuditLogJooqAdapter(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void log(String event, UUID actorUserUuid, UUID targetUserUuid, Map<String, Object> payload) {
        try {
            dsl.execute(
                    "select public.fn_write_audit_log(?, ?, ?, ?::jsonb)",
                    event,
                    actorUserUuid,
                    targetUserUuid,
                    objectMapper.writeValueAsString(payload)
            );
        } catch (JsonProcessingException ex) {
            LOG.warn("audit_log_payload_serialize_failed", ex);
        } catch (Exception ex) {
            LOG.warn("audit_log_insert_failed", ex);
        }
    }
}
