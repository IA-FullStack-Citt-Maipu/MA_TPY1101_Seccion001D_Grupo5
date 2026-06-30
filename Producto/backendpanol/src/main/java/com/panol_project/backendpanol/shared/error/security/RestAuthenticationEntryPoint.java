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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOG = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        LOG.warn("{\"event\":\"client_error_handled\",\"timestamp\":\"{}\",\"endpoint_tag\":\"auth\",\"code\":401,\"error_type\":\"{}\",\"user_uuid\":\"Ninguno\",\"path\":\"{}\",\"cause\":\"{}\"}",
                OffsetDateTime.now(), authException.getClass().getSimpleName(), request.getRequestURI(), authException.getMessage());

        ErrorResponse payload = new ErrorResponse("401", "No autorizado", OffsetDateTime.now());
        objectMapper.writeValue(response.getOutputStream(), payload);
    }
}

