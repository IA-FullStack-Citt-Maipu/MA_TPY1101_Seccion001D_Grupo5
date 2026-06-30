package com.panol_project.backendpanol.modules.email.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panol_project.backendpanol.modules.email.domain.RenderedEmail;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailTemplateRendererTest {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-06-28T15:30:00-04:00");
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-06-30T08:15:00-04:00");
    private static final OffsetDateTime EXPECTED_RETURN_AT = OffsetDateTime.parse("2026-06-30T11:45:00-04:00");
    private static final UUID LOAN_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IMPLEMENT_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        EmailProperties properties = new EmailProperties();
        properties.setFrontendBaseUrl("https://frontend.panol.cl/");
        properties.setFromAddress("notificaciones@panol.cl");

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("mail/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        resolver.setCheckExistence(true);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setEnableSpringELCompiler(true);

        renderer = new EmailTemplateRenderer(templateEngine, properties);
    }

    @Test
    void renderLoanRequestDocenteDebeIncluirBrandingResumenYNota() {
        RenderedEmail rendered = renderer.render("loan.request_registered.docente", baseLoanTemplateData(
                "Solicitud enviada",
                "Tu solicitud PRE-001 fue registrada y quedo pendiente de revision."
        ));

        assertEquals("Solicitud enviada | Panol", rendered.subject());
        assertContainsAll(rendered.html(),
                "Panol Salud",
                "Resumen de tu solicitud",
                "Solicitud enviada",
                "Nota enviada",
                "Necesito el kit de simulacion para la evaluacion practica.",
                "Microscopio digital",
                "30-06-2026, 08:15 hrs",
                "30-06-2026, 11:45 hrs",
                "Ver solicitud",
                "https://frontend.panol.cl#/inventory/prestamos/" + LOAN_UUID
        );
        assertVisibleTextDoesNotContainUuid(rendered.html(), LOAN_UUID);
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderLoanRequestCoordinadorDebeMostrarRevisionDatosYNota() {
        RenderedEmail rendered = renderer.render("loan.request_submitted.coordinador", baseLoanTemplateData(
                "Nueva solicitud de prestamo",
                "El docente Ana Perez ha enviado una nueva solicitud."
        ));

        assertEquals("Nueva solicitud de prestamo | Panol", rendered.subject());
        assertContainsAll(rendered.html(),
                "Solicitud por revisar",
                "Requiere revision",
                "Ana Perez",
                "ana.perez@duocuc.cl",
                "Nota del docente",
                "Necesito el kit de simulacion para la evaluacion practica.",
                "Abrir solicitud"
        );
        assertVisibleTextDoesNotContainUuid(rendered.html(), LOAN_UUID);
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderLoanRequestUpdatedCoordinadorDebeCambiarEyebrow() {
        RenderedEmail rendered = renderer.render("loan.request_updated.coordinador", baseLoanTemplateData(
                "Solicitud actualizada",
                "El docente Ana Perez modifico una solicitud reservada."
        ));

        assertEquals("Solicitud actualizada | Panol", rendered.subject());
        assertContainsAll(rendered.html(),
                "Solicitud actualizada",
                "Solicitud por revisar",
                "Nota del docente",
                "Abrir solicitud"
        );
        assertVisibleTextDoesNotContainUuid(rendered.html(), LOAN_UUID);
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderLoanStatusDocenteAprobadoDebeUsarTemplateGenericoConCopyDeReserva() {
        Map<String, Object> templateData = baseLoanTemplateData(
                "Reserva confirmada",
                "Tu solicitud fue aprobada y comenzaremos la preparacion de los implementos."
        );
        templateData.put("status", "approved");
        templateData.put("status_label", "Aprobada");

        RenderedEmail rendered = renderer.render("loan.status_changed.docente", templateData);

        assertEquals("Reserva confirmada | Panol", rendered.subject());
        assertContainsAll(rendered.html(),
                "Tu reserva fue confirmada",
                "Aprobada",
                "Fecha del evento",
                "Resumen de la reserva",
                "Ver solicitud",
                "Nota original de la solicitud",
                "Necesito el kit de simulacion para la evaluacion practica.",
                "Implementos reservados"
        );
        assertVisibleTextDoesNotContainUuid(rendered.html(), LOAN_UUID);
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderLoanStatusDocentePreparadoDebeUsarCopyDeRetiro() {
        Map<String, Object> templateData = baseLoanTemplateData(
                "Implementos listos para retiro",
                "Tus implementos ya estan listos para retiro."
        );
        templateData.put("status", "prepared");
        templateData.put("status_label", "Lista para retiro");

        RenderedEmail rendered = renderer.render("loan.status_changed.docente", templateData);

        assertEquals("Implementos listos para retiro | Panol", rendered.subject());
        assertContainsAll(rendered.html(),
                "Retiro disponible",
                "Lista para retiro",
                "Resumen del retiro",
                "Implementos listos para retiro",
                "Cantidad lista",
                "Ver solicitud"
        );
        assertVisibleTextDoesNotContainUuid(rendered.html(), LOAN_UUID);
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderLoanRequestCoordinadorDebeConvertirFechaUtcAHoraDeChile() {
        Map<String, Object> templateData = baseLoanTemplateData(
                "Nueva solicitud de prestamo",
                "El docente Ana Perez ha enviado una nueva solicitud."
        );
        templateData.put("scheduled_at", OffsetDateTime.parse("2026-06-29T21:30:00Z"));
        templateData.put("expected_return_at", OffsetDateTime.parse("2026-06-30T00:30:00Z"));

        RenderedEmail rendered = renderer.render("loan.request_submitted.coordinador", templateData);

        assertContainsAll(rendered.html(),
                "29-06-2026, 17:30 hrs",
                "29-06-2026, 20:30 hrs"
        );
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderLoanStatusDocenteEntregadoDebeMostrarSeguimientoYNotaOriginal() {
        Map<String, Object> templateData = baseLoanTemplateData(
                "Prestamo en uso",
                "Tu prestamo ya se encuentra en uso."
        );
        templateData.put("status", "delivered");
        templateData.put("status_label", "En uso");

        RenderedEmail rendered = renderer.render("loan.status_changed.docente", templateData);

        assertEquals("Prestamo en uso | Panol", rendered.subject());
        assertContainsAll(rendered.html(),
                "Prestamo en uso",
                "En uso",
                "Fecha del evento",
                "Seguimiento del prestamo",
                "Nota original de la solicitud",
                "Necesito el kit de simulacion para la evaluacion practica.",
                "Ver prestamo"
        );
        assertVisibleTextDoesNotContainUuid(rendered.html(), LOAN_UUID);
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderLoanRequestDocenteSinNotaNoDebeMostrarBloqueDeNota() {
        Map<String, Object> templateData = baseLoanTemplateData(
                "Solicitud enviada",
                "Tu solicitud fue registrada y quedo pendiente de revision."
        );
        templateData.remove("request_note");

        RenderedEmail rendered = renderer.render("loan.request_registered.docente", templateData);

        assertFalse(rendered.html().contains("Nota enviada"));
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderLoanRequestDocenteConNotaAutomaticaNoDebeMostrarla() {
        Map<String, Object> templateData = baseLoanTemplateData(
                "Solicitud enviada",
                "Tu solicitud fue registrada y quedo pendiente de revision."
        );
        templateData.put("request_note", "Reserva automatica al crear solicitud");

        RenderedEmail rendered = renderer.render("loan.request_registered.docente", templateData);

        assertFalse(rendered.html().contains("Nota enviada"));
        assertFalse(rendered.html().contains("Reserva automatica al crear solicitud"));
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderStockAlertDebeMostrarEstadoCriticoYMetricaDeBrecha() {
        Map<String, Object> templateData = new LinkedHashMap<>();
        templateData.put("event_type", "implement.stock_alert");
        templateData.put("title", "Stock critico");
        templateData.put("message", "El implemento Desfibrilador quedo sin stock disponible.");
        templateData.put("reference_type", "implement");
        templateData.put("reference_id", IMPLEMENT_UUID.toString());
        templateData.put("created_at", CREATED_AT);
        templateData.put("implement_uuid", IMPLEMENT_UUID.toString());
        templateData.put("implement_name", "Desfibrilador");
        templateData.put("available", 0);
        templateData.put("min_stock", 2);
        templateData.put("stock_status", "out_of_stock");

        RenderedEmail rendered = renderer.render("implement.stock_alert.coordinador", templateData);

        assertEquals("Stock critico | Panol", rendered.subject());
        assertContainsAll(rendered.html(),
                "Resumen del stock",
                "Stock critico",
                "Desfibrilador",
                "Faltan",
                "Ver implemento",
                "https://frontend.panol.cl#/inventory/implementos/" + IMPLEMENT_UUID
        );
        assertNoTemplateArtifacts(rendered.html());
    }

    @Test
    void renderPasswordRecoveryDebeMostrarCodigoExpiracionYCta() {
        Map<String, Object> templateData = new LinkedHashMap<>();
        templateData.put("event_type", "auth.password_recovery");
        templateData.put("title", "Recupera tu contrasena");
        templateData.put("message", "Usa este codigo para continuar con la recuperacion de tu contrasena.");
        templateData.put("recipient_name", "Ana Perez");
        templateData.put("verification_code", "AB12CD34");
        templateData.put("expires_in_minutes", 15);
        templateData.put("action_url", "https://frontend.panol.cl/#/recuperar-contrasena/codigo?rut=12345678K");
        templateData.put("created_at", CREATED_AT);

        RenderedEmail rendered = renderer.render("auth.password_recovery", templateData);

        assertEquals("Recupera tu contrasena | Panol", rendered.subject());
        assertContainsAll(rendered.html(),
                "Recuperacion de contrasena",
                "Verifica tu identidad",
                "AB12CD34",
                "15",
                "Ingresar codigo",
                "https://frontend.panol.cl/#/recuperar-contrasena/codigo?rut=12345678K"
        );
        assertNoTemplateArtifacts(rendered.html());
    }

    private Map<String, Object> baseLoanTemplateData(String title, String message) {
        Map<String, Object> templateData = new LinkedHashMap<>();
        templateData.put("event_type", "loan.request_registered");
        templateData.put("title", title);
        templateData.put("message", message);
        templateData.put("reference_type", "loan");
        templateData.put("reference_id", LOAN_UUID.toString());
        templateData.put("created_at", CREATED_AT);
        templateData.put("loan_uuid", LOAN_UUID.toString());
        templateData.put("status", "pending");
        templateData.put("status_label", "Pendiente");
        templateData.put("requester_name", "Ana Perez");
        templateData.put("requester_email", "ana.perez@duocuc.cl");
        templateData.put("room_name", "Sala 204");
        templateData.put("subject_name", "Procedimientos Clinicos");
        templateData.put("scheduled_at", SCHEDULED_AT);
        templateData.put("expected_return_at", EXPECTED_RETURN_AT);
        templateData.put("request_note", "Necesito el kit de simulacion para la evaluacion practica.");
        templateData.put("details", List.of(
                Map.of("implement_name", "Microscopio digital", "requested_quantity", 2),
                Map.of("implement_name", "Guantes de examen", "requested_quantity", 10)
        ));
        return templateData;
    }

    private void assertContainsAll(String html, String... snippets) {
        for (String snippet : snippets) {
            assertTrue(html.contains(snippet), "Expected snippet missing: " + snippet);
        }
    }

    private void assertNoTemplateArtifacts(String html) {
        assertFalse(html.contains("${"), "Rendered html still contains unresolved template markers.");
        assertFalse(html.contains(">null<"), "Rendered html contains null placeholders.");
        assertFalse(html.contains("=\"null\""), "Rendered html contains null attributes.");
    }

    private void assertVisibleTextDoesNotContainUuid(String html, UUID uuid) {
        String visibleText = extractVisibleText(html);
        assertFalse(visibleText.contains(uuid.toString()), "Visible email text must not expose UUIDs.");
        assertTrue(html.contains("https://frontend.panol.cl#/inventory/prestamos/" + uuid),
                "Expected CTA href missing UUID route.");
    }

    private String extractVisibleText(String html) {
        return TAG_PATTERN.matcher(html)
                .replaceAll(" ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
