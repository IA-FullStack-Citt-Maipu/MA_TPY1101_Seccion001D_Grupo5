package com.panol_project.backendpanol.shared.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        logStructured("client_error_handled", request, ex.getStatus().value(), ex.getCode(), ex.getClass().getSimpleName(), ex.getMessage(), null, null);
        return buildResponse(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String cause = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining("; "));
        logStructured("client_error_handled", request, 400, "VALIDATION_ERROR", ex.getClass().getSimpleName(), cause, null, null);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", cause);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        logStructured("client_error_handled", request, 400, "VALIDATION_ERROR", ex.getClass().getSimpleName(), ex.getMessage(), null, null);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        logStructured("client_error_handled", request, 400, "MALFORMED_JSON", ex.getClass().getSimpleName(), ex.getMessage(), null, null);
        return buildResponse(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Payload JSON invalido");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        logStructured("client_error_handled", request, 400, "INVALID_ARGUMENT", ex.getClass().getSimpleName(), ex.getMessage(), null, null);
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String errorCode = isDuplicateKey(ex) ? "DUPLICATE_RESOURCE" : "DATA_INTEGRITY_VIOLATION";
        logStructured("client_error_handled", request, 409, errorCode, ex.getClass().getSimpleName(), cause, null, null);
        return buildResponse(HttpStatus.CONFLICT, errorCode, "No fue posible procesar la solicitud");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        logStructured("client_error_handled", request, 403, "FORBIDDEN", ex.getClass().getSimpleName(), ex.getMessage(), null, null);
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", publicMessage(HttpStatus.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        logStructured("server_error_handled", request, 500, "INTERNAL_ERROR", ex.getClass().getSimpleName(), ex.getMessage(), null, ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", publicMessage(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message) {
        String safeCode = code == null || code.isBlank() ? String.valueOf(status.value()) : code;
        String safeMessage = message == null || message.isBlank() ? publicMessage(status) : message;
        return ResponseEntity.status(status).body(new ErrorResponse(safeCode, safeMessage, OffsetDateTime.now()));
    }

    private String publicMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Solicitud invalida";
            case UNAUTHORIZED -> "No autorizado";
            case FORBIDDEN -> "Acceso denegado";
            case NOT_FOUND -> "Recurso no encontrado";
            case CONFLICT, TOO_MANY_REQUESTS -> "No fue posible procesar la solicitud";
            default -> "Error interno del servidor";
        };
    }

    private void logStructured(String event, HttpServletRequest request, int code, String codeText, String errorType, String cause, String userUuid, Throwable ex) {
        String endpointTag = endpointTag(request.getRequestURI());
        Map<String, Object> payload = Map.of(
                "event", event,
                "timestamp", OffsetDateTime.now().toString(),
                "endpoint_tag", endpointTag,
                "code", code,
                "error_type", errorType,
                "user_uuid", userUuid == null ? "Ninguno" : userUuid,
                "path", request.getRequestURI(),
                "cause", cause == null ? "N/A" : cause);

        String asJson;
        try {
            asJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            asJson = payload.toString();
        }

        if (code >= 500) {
            LOG.error(asJson, ex);
        } else {
            LOG.warn(asJson);
        }
    }

    private String endpointTag(String path) {
        if (path == null || path.isBlank()) return "unknown";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        if (parts.length >= 3 && "api".equals(parts[0])) {
            return parts[2];
        }
        return parts[0];
    }

    private boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("duplicate key") || message.contains("SQLSTATE 23505"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

