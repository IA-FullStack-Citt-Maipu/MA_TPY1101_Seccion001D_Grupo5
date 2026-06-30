package com.panol_project.backendpanol.modules.auth.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.modules.auth.domain.PasswordRecoveryNotificationPort;
import com.panol_project.backendpanol.modules.email.application.EmailProperties;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class PasswordRecoveryEmailOutboxAdapter implements PasswordRecoveryNotificationPort {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final EmailProperties emailProperties;

    public PasswordRecoveryEmailOutboxAdapter(DSLContext dsl, ObjectMapper objectMapper, EmailProperties emailProperties) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.emailProperties = emailProperties;
    }

    @Override
    public boolean isAvailable() {
        return emailProperties.isEnabled() && StringUtils.hasText(emailProperties.getFrontendBaseUrl());
    }

    @Override
    public void enqueuePasswordRecoveryEmail(
            String recipientName,
            String recipientEmail,
            String rut,
            String verificationCode,
            int expiresInMinutes
    ) {
        if (!StringUtils.hasText(recipientEmail)) {
            return;
        }

        Map<String, Object> templateData = new LinkedHashMap<>();
        templateData.put("event_type", "auth.password_recovery");
        templateData.put("title", "Codigo de verificacion");
        templateData.put("message", "Usa este codigo para continuar con la recuperacion de tu contrasena.");
        templateData.put("recipient_name", recipientName);
        templateData.put("rut", rut);
        templateData.put("verification_code", verificationCode);
        templateData.put("expires_in_minutes", expiresInMinutes);
        templateData.put("created_at", OffsetDateTime.now());
        templateData.put("action_url", normalizeFrontendBaseUrl() + "#/recuperar-contrasena/codigo?rut=" + rut);

        dsl.execute("""
                insert into public.email_outbox (
                    event_id,
                    recipient_email,
                    email_type,
                    template_data,
                    status,
                    retry_count,
                    occurred_at
                )
                values (
                    cast(? as uuid),
                    cast(? as text),
                    cast(? as text),
                    cast(? as jsonb),
                    'PENDING',
                    0,
                    now()
                )
                """,
                UUID.randomUUID(),
                recipientEmail.trim().toLowerCase(),
                "auth.password_recovery",
                serializeTemplateData(templateData)
        );
    }

    private String serializeTemplateData(Map<String, Object> templateData) {
        try {
            return objectMapper.writeValueAsString(templateData);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible serializar template_data para password recovery", ex);
        }
    }

    private String normalizeFrontendBaseUrl() {
        String baseUrl = emailProperties.getFrontendBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "http://localhost:18081/";
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }
}
