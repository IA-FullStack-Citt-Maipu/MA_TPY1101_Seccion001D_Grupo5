package com.panol_project.backendpanol.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.shared.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class AiAgentRequestFilter extends OncePerRequestFilter {

    private static final String API_V2_PREFIX = "/api/v2/";
    private static final String AI_AGENT_ORIGIN = "AI-Agent";
    private static final String CLIENT_ORIGIN_HEADER = "X-Client-Origin";
    private static final String CLIENT_SECRET_HEADER = "X-Client-Secret";
    private static final String SECRET_INVALID_CODE = "AI_AGENT_SECRET_INVALID";
    private static final String SECRET_INVALID_MESSAGE = "Credenciales de origen AI-Agent invalidas.";
    private static final String MUTATION_FORBIDDEN_CODE = "AI_AGENT_MUTATION_FORBIDDEN";
    private static final String MUTATION_FORBIDDEN_MESSAGE = "El origen AI-Agent solo tiene permisos de lectura.";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final ObjectMapper objectMapper;
    private final byte[] expectedSecretBytes;

    public AiAgentRequestFilter(ObjectMapper objectMapper, String aiAgentSecret) {
        this.objectMapper = objectMapper;
        this.expectedSecretBytes = aiAgentSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith(API_V2_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientOrigin = request.getHeader(CLIENT_ORIGIN_HEADER);
        if (!AI_AGENT_ORIGIN.equals(clientOrigin)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!hasValidSecret(request.getHeader(CLIENT_SECRET_HEADER))) {
            writeForbidden(response, SECRET_INVALID_CODE, SECRET_INVALID_MESSAGE);
            return;
        }

        if (!SAFE_METHODS.contains(request.getMethod())) {
            writeForbidden(response, MUTATION_FORBIDDEN_CODE, MUTATION_FORBIDDEN_MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasValidSecret(String providedSecret) {
        if (providedSecret == null || providedSecret.isBlank() || expectedSecretBytes.length == 0) {
            return false;
        }

        byte[] providedBytes = providedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedSecretBytes, providedBytes);
    }

    private void writeForbidden(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ErrorResponse(code, message, OffsetDateTime.now())
        );
    }
}
