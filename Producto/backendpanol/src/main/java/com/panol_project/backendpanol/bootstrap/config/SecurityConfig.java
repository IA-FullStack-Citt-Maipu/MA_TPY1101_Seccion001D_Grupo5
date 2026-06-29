package com.panol_project.backendpanol.bootstrap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panol_project.backendpanol.modules.auth.infrastructure.BotTokenScopeValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.panol_project.backendpanol.modules.auth.infrastructure.TokenRevocationValidator;
import com.panol_project.backendpanol.shared.error.security.RestAccessDeniedHandler;
import com.panol_project.backendpanol.shared.error.security.RestAuthenticationEntryPoint;
import com.panol_project.backendpanol.shared.security.AiAgentRequestFilter;
import com.panol_project.backendpanol.shared.security.CookieOrHeaderBearerTokenResolver;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AiAgentRequestFilter aiAgentRequestFilter,
            BearerTokenResolver bearerTokenResolver,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(aiAgentRequestFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v2/auth/login", "/api/v2/auth/logout", "/api/v2/auth/refresh").permitAll()
                        .requestMatchers("/internal/**", "/api/v1/**").denyAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                token -> new JwtAuthenticationToken(token, extractAuthorities(token)))))
                .build();
    }

    @Bean
    AiAgentRequestFilter aiAgentRequestFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.ai-agent.secret}") String aiAgentSecret
    ) {
        return new AiAgentRequestFilter(objectMapper, aiAgentSecret);
    }

    @Bean
    BearerTokenResolver bearerTokenResolver(CookieOrHeaderBearerTokenResolver resolver) {
        return resolver;
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.bot-token.audience:bot-panol}") String botTokenAudience,
            TokenRevocationValidator tokenRevocationValidator
    ) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();
        OAuth2TokenValidator<Jwt> withDefaults = JwtValidators.createDefault();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                withDefaults,
                tokenRevocationValidator,
                new BotTokenScopeValidator(botTokenAudience)
        ));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.auth.jwt.secret}") String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Jwt jwt) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        addRole(roles, jwt.getClaim("role"));
        addRole(roles, jwt.getClaim("user_role"));
        addRole(roles, jwt.getClaim("roles"));

        Object appMetadataClaim = jwt.getClaim("app_metadata");
        if (appMetadataClaim instanceof Map<?, ?> appMetadata) {
            addRole(roles, appMetadata.get("role"));
            addRole(roles, appMetadata.get("roles"));
        }

        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(String::toUpperCase)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private void addRole(Collection<String> roles, Object claim) {
        if (claim instanceof String role) {
            roles.add(role);
            return;
        }

        if (claim instanceof Collection<?> values) {
            values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(roles::add);
        }
    }
}

