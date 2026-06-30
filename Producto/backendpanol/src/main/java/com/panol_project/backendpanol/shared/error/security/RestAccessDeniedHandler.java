package com.panol_project.backendpanol.shared.error.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.shared.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestAccessDeniedHandler.class);
    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        LOG.warn("{\"event\":\"client_error_handled\",\"timestamp\":\"{}\",\"endpoint_tag\":\"auth\",\"code\":403,\"error_type\":\"{}\",\"user_uuid\":\"Ninguno\",\"path\":\"{}\",\"cause\":\"{}\"}",
                OffsetDateTime.now(), accessDeniedException.getClass().getSimpleName(), request.getRequestURI(), accessDeniedException.getMessage());

        ErrorResponse payload = new ErrorResponse("403", "Acceso denegado", OffsetDateTime.now());
        objectMapper.writeValue(response.getOutputStream(), payload);
    }
}

