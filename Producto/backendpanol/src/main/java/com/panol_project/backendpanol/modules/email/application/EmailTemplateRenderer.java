package com.panol_project.backendpanol.modules.email.application;

import com.panol_project.backendpanol.modules.email.domain.RenderedEmail;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class EmailTemplateRenderer {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy, hh:mm a", new Locale("es", "CL"));

    private final SpringTemplateEngine emailTemplateEngine;
    private final EmailProperties emailProperties;

    public EmailTemplateRenderer(
            @Qualifier("emailTemplateEngine") SpringTemplateEngine emailTemplateEngine,
            EmailProperties emailProperties
    ) {
        this.emailTemplateEngine = emailTemplateEngine;
        this.emailProperties = emailProperties;
    }

    public RenderedEmail render(String emailType, Map<String, Object> templateData) {
        Context context = new Context(new Locale("es", "CL"));
        templateData.forEach(context::setVariable);
        context.setVariable("details", normalizeDetails(templateData.get("details")));
        context.setVariable("createdAtFormatted", formatDate(templateData.get("created_at")));
        context.setVariable("scheduledAtFormatted", formatDate(templateData.get("scheduled_at")));
        context.setVariable("expectedReturnAtFormatted", formatDate(templateData.get("expected_return_at")));
        context.setVariable("subject", resolveSubject(emailType, templateData));
        context.setVariable("previewText", resolvePreviewText(templateData));
        context.setVariable("actionUrl", resolveActionUrl(templateData));
        context.setVariable("actionLabel", "Ver detalle");

        String html = emailTemplateEngine.process(resolveTemplateName(emailType), context);
        return new RenderedEmail(resolveSubject(emailType, templateData), html);
    }

    private String resolveTemplateName(String emailType) {
        return switch (emailType) {
            case "loan.request_registered.docente" -> "loan-request-docente";
            case "loan.request_submitted.coordinador", "loan.request_updated.coordinador" -> "loan-request-coordinador";
            case "loan.status_changed.docente" -> "loan-status-docente";
            case "implement.stock_alert.coordinador", "implement.stock_alert.director" -> "stock-alert";
            default -> throw new IllegalArgumentException("email_type no soportado: " + emailType);
        };
    }

    private String resolveSubject(String emailType, Map<String, Object> templateData) {
        String title = asString(templateData.get("title"));
        if (StringUtils.hasText(title)) {
            return title + " | Panol";
        }
        return switch (emailType) {
            case "loan.request_registered.docente" -> "Solicitud enviada | Panol";
            case "loan.request_submitted.coordinador" -> "Nueva solicitud de prestamo | Panol";
            case "loan.request_updated.coordinador" -> "Solicitud pendiente actualizada | Panol";
            case "loan.status_changed.docente" -> "Actualizacion de prestamo | Panol";
            case "implement.stock_alert.coordinador", "implement.stock_alert.director" -> "Alerta de stock | Panol";
            default -> "Notificacion | Panol";
        };
    }

    private String resolvePreviewText(Map<String, Object> templateData) {
        String message = asString(templateData.get("message"));
        if (StringUtils.hasText(message)) {
            return message;
        }
        return "Tienes una nueva notificacion en Panol.";
    }

    private String resolveActionUrl(Map<String, Object> templateData) {
        String referenceType = asString(templateData.get("reference_type"));
        String referenceId = asString(templateData.get("reference_id"));
        if (!StringUtils.hasText(referenceType) || !StringUtils.hasText(referenceId)) {
            return null;
        }

        String baseUrl = emailProperties.getFrontendBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return switch (referenceType) {
            case "loan" -> normalizedBaseUrl + "#/inventory/prestamos/" + referenceId;
            case "implement" -> normalizedBaseUrl + "#/inventory/implementos/" + referenceId;
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeDetails(Object rawDetails) {
        if (rawDetails instanceof List<?> details) {
            return details.stream()
                    .filter(Map.class::isInstance)
                    .map(detail -> (Map<String, Object>) detail)
                    .toList();
        }
        return List.of();
    }

    private String formatDate(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof OffsetDateTime offsetDateTime) {
            return DATE_FORMATTER.format(offsetDateTime);
        }
        try {
            return DATE_FORMATTER.format(OffsetDateTime.parse(rawValue.toString()));
        } catch (Exception ex) {
            return rawValue.toString();
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
