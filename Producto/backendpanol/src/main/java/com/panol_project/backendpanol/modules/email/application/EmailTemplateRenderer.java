package com.panol_project.backendpanol.modules.email.application;

import com.panol_project.backendpanol.modules.email.domain.RenderedEmail;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.Year;
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

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy, HH:mm 'hrs'", new Locale("es", "CL"));
    private static final List<String> SYSTEM_GENERATED_REQUEST_NOTES = List.of(
            "Reserva automatica al crear solicitud",
            "Solicitud modificada por docente"
    );

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
        String statusTone = resolveStatusTone(emailType, templateData);
        templateData.forEach(context::setVariable);
        context.setVariable("emailType", emailType);
        context.setVariable("details", normalizeDetails(templateData.get("details")));
        context.setVariable("requestNote", resolveRequestNote(templateData.get("request_note")));
        context.setVariable("createdAtFormatted", formatDate(templateData.get("created_at")));
        context.setVariable("scheduledAtFormatted", formatDate(templateData.get("scheduled_at")));
        context.setVariable("expectedReturnAtFormatted", formatDate(templateData.get("expected_return_at")));
        context.setVariable("emailVariant", resolveEmailVariant(emailType));
        context.setVariable("headerEyebrow", resolveHeaderEyebrow(emailType, templateData));
        context.setVariable("headerSubtitle", resolveHeaderSubtitle(emailType, templateData));
        context.setVariable("sectionTitle", resolveSectionTitle(emailType, templateData));
        context.setVariable("statusTone", statusTone);
        context.setVariable("statusLabel", resolveStatusLabel(emailType, templateData));
        context.setVariable("badgeStyle", resolveBadgeStyle(statusTone));
        context.setVariable("stockGap", resolveStockGap(templateData));
        context.setVariable("subject", resolveSubject(emailType, templateData));
        context.setVariable("previewText", resolvePreviewText(templateData));
        context.setVariable("actionUrl", resolveActionUrl(templateData));
        context.setVariable("actionLabel", resolveActionLabel(emailType, templateData));
        context.setVariable("docenteSummaryTitle", resolveDocenteSummaryTitle(emailType, templateData));
        context.setVariable("docenteRequestNoteTitle", resolveDocenteRequestNoteTitle(emailType));
        context.setVariable("docenteDetailsTitle", resolveDocenteDetailsTitle(emailType, templateData));
        context.setVariable("docenteQuantityLabel", resolveDocenteQuantityLabel(emailType, templateData));
        context.setVariable("supportEmail", emailProperties.getFromAddress());
        context.setVariable("currentYear", Year.now().getValue());

        String html = emailTemplateEngine.process(resolveTemplateName(emailType), context);
        return new RenderedEmail(resolveSubject(emailType, templateData), html);
    }

    private String resolveTemplateName(String emailType) {
        return switch (emailType) {
            case "loan.request_registered.docente" -> "loan-request-docente";
            case "loan.request_submitted.coordinador", "loan.request_updated.coordinador" -> "loan-request-coordinador";
            case "loan.status_changed.docente" -> "loan-request-docente";
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
            case "loan.status_changed.docente" -> resolveLoanStatusSubject(templateData);
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

    private String resolveEmailVariant(String emailType) {
        return switch (emailType) {
            case "loan.request_registered.docente",
                 "loan.status_changed.docente" -> "loan-docente";
            case "loan.request_submitted.coordinador",
                 "loan.request_updated.coordinador" -> "loan-request";
            case "implement.stock_alert.coordinador",
                 "implement.stock_alert.director" -> "stock-alert";
            default -> "notification";
        };
    }

    private String resolveHeaderEyebrow(String emailType, Map<String, Object> templateData) {
        String eventType = asString(templateData.get("event_type"));
        if ("loan.request_updated.coordinador".equals(emailType) || "loan.request_updated".equals(eventType)) {
            return "Solicitud actualizada";
        }
        return switch (emailType) {
            case "loan.request_registered.docente" -> "Solicitud registrada";
            case "loan.request_submitted.coordinador" -> "Revision requerida";
            case "loan.status_changed.docente" -> resolveDocenteStatusEyebrow(templateData);
            case "implement.stock_alert.coordinador", "implement.stock_alert.director" -> "Alerta operativa";
            default -> "Notificacion automatica";
        };
    }

    private String resolveHeaderSubtitle(String emailType, Map<String, Object> templateData) {
        return switch (emailType) {
            case "loan.request_registered.docente" ->
                    "Te mantendremos al tanto del avance de tu solicitud desde el mismo flujo operativo del sistema.";
            case "loan.request_submitted.coordinador", "loan.request_updated.coordinador" ->
                    "Revisa los datos clave para continuar la validacion sin salir del estandar visual del panel.";
            case "loan.status_changed.docente" -> resolveDocenteStatusSubtitle(templateData);
            case "implement.stock_alert.coordinador", "implement.stock_alert.director" ->
                    "Recibe visibilidad temprana para reaccionar antes de afectar la continuidad operativa.";
            default -> "Actualizacion automatica generada por Panol Salud.";
        };
    }

    private String resolveSectionTitle(String emailType, Map<String, Object> templateData) {
        return switch (emailType) {
            case "loan.request_registered.docente" -> "Resumen de tu solicitud";
            case "loan.request_submitted.coordinador", "loan.request_updated.coordinador" -> "Solicitud por revisar";
            case "loan.status_changed.docente" -> resolveDocenteStatusSectionTitle(templateData);
            case "implement.stock_alert.coordinador", "implement.stock_alert.director" -> "Resumen del stock";
            default -> "Resumen operativo";
        };
    }

    private String resolveStatusTone(String emailType, Map<String, Object> templateData) {
        String stockStatus = asString(templateData.get("stock_status"));
        if (StringUtils.hasText(stockStatus)) {
            return switch (stockStatus) {
                case "out_of_stock" -> "danger";
                case "low_stock" -> "warning";
                default -> "info";
            };
        }

        String status = asString(templateData.get("status"));
        if (StringUtils.hasText(status)) {
            return switch (status) {
                case "prepared", "completed" -> "success";
                case "pending", "approved", "delivered" -> "info";
                case "overdue", "expired" -> "warning";
                case "rejected", "cancelled" -> "danger";
                default -> "info";
            };
        }

        return switch (emailType) {
            case "loan.request_submitted.coordinador", "loan.request_updated.coordinador" -> "warning";
            case "implement.stock_alert.coordinador", "implement.stock_alert.director" -> "warning";
            default -> "info";
        };
    }

    private String resolveStatusLabel(String emailType, Map<String, Object> templateData) {
        String stockStatus = asString(templateData.get("stock_status"));
        if (StringUtils.hasText(stockStatus)) {
            return switch (stockStatus) {
                case "out_of_stock" -> "Stock critico";
                case "low_stock" -> "Stock bajo";
                default -> "Alerta";
            };
        }

        String statusLabel = asString(templateData.get("status_label"));
        if (StringUtils.hasText(statusLabel)) {
            return statusLabel;
        }

        String status = asString(templateData.get("status"));
        if (StringUtils.hasText(status)) {
            return switch (status) {
                case "pending" -> "Pendiente";
                case "approved" -> "Aprobada";
                case "prepared" -> "Lista para retiro";
                case "delivered" -> "En uso";
                case "completed" -> "Finalizada";
                case "rejected" -> "Rechazada";
                case "cancelled" -> "Cancelada";
                case "expired" -> "Vencida";
                case "overdue" -> "Atrasada";
                default -> null;
            };
        }

        return switch (emailType) {
            case "loan.request_submitted.coordinador", "loan.request_updated.coordinador" -> "Pendiente";
            case "loan.request_registered.docente" -> "Registrada";
            default -> null;
        };
    }

    private String resolveBadgeStyle(String statusTone) {
        String baseStyle = "display:inline-block;padding:8px 14px;border-radius:999px;"
                + "font-family:'Inter','Segoe UI',Arial,sans-serif;font-size:12px;font-weight:700;"
                + "letter-spacing:0.02em;text-align:center;";
        return switch (statusTone) {
            case "success" -> baseStyle + "background-color:#e8f7ef;border:1px solid #b9e6cc;color:#1e8a59;";
            case "warning" -> baseStyle + "background-color:#fff4df;border:1px solid #f1d49a;color:#9f6503;";
            case "danger" -> baseStyle + "background-color:#fff0f2;border:1px solid #f0b4c0;color:#9f2940;";
            default -> baseStyle + "background-color:#dff6fb;border:1px solid #a7e4f0;color:#0b5b8f;";
        };
    }

    private Integer resolveStockGap(Map<String, Object> templateData) {
        Integer available = asInteger(templateData.get("available"));
        Integer minStock = asInteger(templateData.get("min_stock"));
        if (available == null || minStock == null) {
            return null;
        }
        return Math.max(minStock - available, 0);
    }

    private String resolveActionLabel(String emailType, Map<String, Object> templateData) {
        String referenceType = asString(templateData.get("reference_type"));
        if ("implement".equals(referenceType)) {
            return "Ver implemento";
        }
        String status = asString(templateData.get("status"));
        return switch (emailType) {
            case "loan.request_registered.docente" -> "Ver solicitud";
            case "loan.request_submitted.coordinador", "loan.request_updated.coordinador" -> "Abrir solicitud";
            case "loan.status_changed.docente" ->
                    isPreDeliveryStatus(status) ? "Ver solicitud" : "Ver prestamo";
            default -> "Ver detalle";
        };
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

    private String resolveRequestNote(Object rawRequestNote) {
        String requestNote = normalizeOptionalText(rawRequestNote);
        if (!StringUtils.hasText(requestNote)) {
            return null;
        }
        boolean isSystemGenerated = SYSTEM_GENERATED_REQUEST_NOTES.stream()
                .anyMatch(systemNote -> systemNote.equalsIgnoreCase(requestNote));
        return isSystemGenerated ? null : requestNote;
    }

    private String resolveLoanStatusSubject(Map<String, Object> templateData) {
        String status = asString(templateData.get("status"));
        return switch (status) {
            case "approved" -> "Reserva confirmada | Panol";
            case "prepared" -> "Implementos listos para retiro | Panol";
            case "delivered" -> "Prestamo en uso | Panol";
            case "completed" -> "Prestamo finalizado | Panol";
            case "rejected" -> "Solicitud rechazada | Panol";
            case "cancelled" -> "Solicitud cancelada | Panol";
            case "expired" -> "Solicitud vencida | Panol";
            case "overdue" -> "Prestamo atrasado | Panol";
            default -> "Actualizacion de prestamo | Panol";
        };
    }

    private String resolveDocenteStatusEyebrow(Map<String, Object> templateData) {
        String status = asString(templateData.get("status"));
        return switch (status) {
            case "approved" -> "Reserva confirmada";
            case "prepared" -> "Retiro disponible";
            case "delivered" -> "Prestamo en curso";
            case "completed" -> "Cierre del prestamo";
            case "rejected" -> "Revision completada";
            case "cancelled" -> "Flujo detenido";
            case "expired" -> "Accion requerida";
            case "overdue" -> "Seguimiento urgente";
            default -> "Seguimiento del prestamo";
        };
    }

    private String resolveDocenteStatusSubtitle(Map<String, Object> templateData) {
        String status = asString(templateData.get("status"));
        return switch (status) {
            case "approved" ->
                    "Tu solicitud ya fue aprobada y el equipo comenzara la preparacion de los implementos segun la programacion registrada.";
            case "prepared" ->
                    "Tus implementos ya estan listos para retiro, por lo que puedes acercarte al panol dentro del horario planificado.";
            case "delivered" ->
                    "La entrega ya fue registrada y tu prestamo se encuentra activo dentro del flujo operativo del sistema.";
            case "completed" ->
                    "El prestamo fue cerrado correctamente y queda trazabilidad disponible desde el mismo detalle de la solicitud.";
            case "rejected" ->
                    "Tu solicitud no pudo continuar. Revisa el detalle para conocer la observacion registrada por el equipo.";
            case "cancelled" ->
                    "La solicitud fue cancelada y el flujo quedo detenido antes de la entrega de implementos.";
            case "expired" ->
                    "La solicitud vencio por tiempo y ya no puede continuar sin una nueva gestion desde el sistema.";
            case "overdue" ->
                    "El sistema detecto que la fecha estimada de devolucion fue superada y conviene regularizar el prestamo cuanto antes.";
            default ->
                    "Este correo resume el ultimo cambio relevante de tu prestamo para que puedas actuar a tiempo.";
        };
    }

    private String resolveDocenteStatusSectionTitle(Map<String, Object> templateData) {
        String status = asString(templateData.get("status"));
        return switch (status) {
            case "approved" -> "Tu reserva fue confirmada";
            case "prepared" -> "Retiro disponible";
            case "delivered" -> "Prestamo en uso";
            case "completed" -> "Prestamo finalizado";
            case "rejected" -> "Resultado de la revision";
            case "cancelled" -> "Solicitud cancelada";
            case "expired" -> "Solicitud vencida";
            case "overdue" -> "Prestamo atrasado";
            default -> "Estado actualizado";
        };
    }

    private String resolveDocenteSummaryTitle(String emailType, Map<String, Object> templateData) {
        if ("loan.request_registered.docente".equals(emailType)) {
            return "Resumen de la solicitud";
        }
        if (!"loan.status_changed.docente".equals(emailType)) {
            return null;
        }
        String status = asString(templateData.get("status"));
        return switch (status) {
            case "approved" -> "Resumen de la reserva";
            case "prepared" -> "Resumen del retiro";
            default -> "Seguimiento del prestamo";
        };
    }

    private String resolveDocenteRequestNoteTitle(String emailType) {
        return "loan.request_registered.docente".equals(emailType)
                ? "Nota enviada"
                : "Nota original de la solicitud";
    }

    private String resolveDocenteDetailsTitle(String emailType, Map<String, Object> templateData) {
        if ("loan.request_registered.docente".equals(emailType)) {
            return "Implementos solicitados";
        }
        if (!"loan.status_changed.docente".equals(emailType)) {
            return null;
        }
        String status = asString(templateData.get("status"));
        return switch (status) {
            case "approved" -> "Implementos reservados";
            case "prepared" -> "Implementos listos para retiro";
            default -> "Implementos considerados";
        };
    }

    private String resolveDocenteQuantityLabel(String emailType, Map<String, Object> templateData) {
        if ("loan.request_registered.docente".equals(emailType)) {
            return "Cantidad solicitada";
        }
        if (!"loan.status_changed.docente".equals(emailType)) {
            return "Cantidad";
        }
        String status = asString(templateData.get("status"));
        return switch (status) {
            case "prepared" -> "Cantidad lista";
            case "approved" -> "Cantidad reservada";
            default -> "Cantidad vinculada";
        };
    }

    private boolean isPreDeliveryStatus(String status) {
        return "pending".equals(status) || "approved".equals(status) || "prepared".equals(status);
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
            return DATE_FORMATTER.format(offsetDateTime.atZoneSameInstant(DISPLAY_ZONE));
        }
        try {
            return DATE_FORMATTER.format(OffsetDateTime.parse(rawValue.toString()).atZoneSameInstant(DISPLAY_ZONE));
        } catch (Exception ex) {
            return rawValue.toString();
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalizeOptionalText(Object value) {
        String rawText = asString(value);
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        String normalized = rawText.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
