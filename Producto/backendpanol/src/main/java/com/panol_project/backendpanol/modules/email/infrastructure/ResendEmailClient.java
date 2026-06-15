package com.panol_project.backendpanol.modules.email.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.panol_project.backendpanol.modules.email.application.EmailProperties;
import com.panol_project.backendpanol.modules.email.domain.EmailDeliveryException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ResendEmailClient {

    private final EmailProperties properties;
    private final RestClient restClient;

    public ResendEmailClient(EmailProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(buildRequestFactory(properties))
                .build();
    }

    public String send(String recipientEmail, String subject, String html) {
        if (!"resend".equalsIgnoreCase(properties.getProvider())) {
            throw new EmailDeliveryException("Proveedor de email no soportado: " + properties.getProvider());
        }
        if (!StringUtils.hasText(properties.getResendApiKey())) {
            throw new EmailDeliveryException("APP_EMAIL_RESEND_API_KEY no esta configurada");
        }

        try {
            ResendSendEmailResponse response = restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getResendApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendSendEmailRequest(
                            resolveFromAddress(),
                            List.of(recipientEmail),
                            subject,
                            html
                    ))
                    .retrieve()
                    .body(ResendSendEmailResponse.class);

            if (response == null || !StringUtils.hasText(response.id())) {
                throw new EmailDeliveryException("Resend no devolvio un id de mensaje valido");
            }
            return response.id();
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            throw new EmailDeliveryException(
                    "Resend respondio " + ex.getRawStatusCode() + ": " + responseBody,
                    ex
            );
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EmailDeliveryException("No fue posible llamar a Resend", ex);
        }
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(EmailProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return factory;
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(properties.getFromName())) {
            return properties.getFromName() + " <" + properties.getFromAddress() + ">";
        }
        return properties.getFromAddress();
    }

    private record ResendSendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResendSendEmailResponse(
            String id
    ) {
    }
}
