package com.panol_project.backendpanol.bootstrap.config;

import com.panol_project.backendpanol.shared.persistence.rls.AppCurrentUserRlsExecuteListener;
import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class JooqRlsConfig {

    @Bean
    @Order(1)
    ExecuteListenerProvider appCurrentUserRlsExecuteListenerProvider(AppCurrentUserRlsExecuteListener listener) {
        return new DefaultExecuteListenerProvider(listener);
    }
}
