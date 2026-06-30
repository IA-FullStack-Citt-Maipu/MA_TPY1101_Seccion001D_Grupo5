package com.panol_project.backendpanol.bootstrap.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Value("${CORS_ALLOWED_ORIGINS:}")
    private String corsAllowedOrigins;

    @Value("${FRONTEND_ORIGIN:}")
    private String frontendOrigin;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:18081",
                "http://127.0.0.1:18081",
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        ));

        allowedOrigins.addAll(parseOrigins(corsAllowedOrigins));
        allowedOrigins.addAll(parseOrigins(frontendOrigin));

        config.setAllowedOrigins(allowedOrigins.stream().distinct().toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> parseOrigins(String rawOrigins) {
        if (rawOrigins == null || rawOrigins.isBlank()) {
            return List.of();
        }
        return List.of(rawOrigins.split(","))
                .stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .filter(Objects::nonNull)
                .toList();
    }
}
