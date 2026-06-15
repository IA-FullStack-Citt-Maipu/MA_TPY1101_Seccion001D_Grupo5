package com.panol_project.backendpanol.bootstrap.config;

import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("cloudsql")
public class FlywayRlsConfig {

    @Bean
    FlywayConfigurationCustomizer flywayConfigurationCustomizer(
            @Value("${app.outbox.system-user-uuid:99999999-9999-9999-9999-999999999999}") String systemUserUuid
    ) {
        String normalizedSystemUserUuid = systemUserUuid.trim();
        String sessionSql = "SELECT set_config('app.current_user_uuid', '" + normalizedSystemUserUuid + "', false)";
        String disableForceRlsSql = """
                DO $$
                BEGIN
                    IF to_regclass('public."user"') IS NOT NULL THEN
                        EXECUTE 'ALTER TABLE public."user" NO FORCE ROW LEVEL SECURITY';
                    END IF;
                    IF to_regclass('public.loan') IS NOT NULL THEN
                        EXECUTE 'ALTER TABLE public.loan NO FORCE ROW LEVEL SECURITY';
                    END IF;
                    IF to_regclass('public.notification') IS NOT NULL THEN
                        EXECUTE 'ALTER TABLE public.notification NO FORCE ROW LEVEL SECURITY';
                    END IF;
                    IF to_regclass('public.audit_log') IS NOT NULL THEN
                        EXECUTE 'ALTER TABLE public.audit_log NO FORCE ROW LEVEL SECURITY';
                    END IF;
                    IF to_regclass('public.inventory_movement') IS NOT NULL THEN
                        EXECUTE 'ALTER TABLE public.inventory_movement NO FORCE ROW LEVEL SECURITY';
                    END IF;
                    IF to_regclass('public.stock') IS NOT NULL THEN
                        EXECUTE 'ALTER TABLE public.stock NO FORCE ROW LEVEL SECURITY';
                    END IF;
                END
                $$;
                """;
        String enableForceRlsSql = """
                DO $$
                BEGIN
                    IF current_setting('server_version_num')::int >= 170000 THEN
                        IF to_regclass('public."user"') IS NOT NULL THEN
                            EXECUTE 'ALTER TABLE public."user" FORCE ROW LEVEL SECURITY';
                        END IF;
                        IF to_regclass('public.loan') IS NOT NULL THEN
                            EXECUTE 'ALTER TABLE public.loan FORCE ROW LEVEL SECURITY';
                        END IF;
                        IF to_regclass('public.notification') IS NOT NULL THEN
                            EXECUTE 'ALTER TABLE public.notification FORCE ROW LEVEL SECURITY';
                        END IF;
                        IF to_regclass('public.audit_log') IS NOT NULL THEN
                            EXECUTE 'ALTER TABLE public.audit_log FORCE ROW LEVEL SECURITY';
                        END IF;
                        IF to_regclass('public.inventory_movement') IS NOT NULL THEN
                            EXECUTE 'ALTER TABLE public.inventory_movement FORCE ROW LEVEL SECURITY';
                        END IF;
                        IF to_regclass('public.stock') IS NOT NULL THEN
                            EXECUTE 'ALTER TABLE public.stock FORCE ROW LEVEL SECURITY';
                        END IF;
                    END IF;
                END
                $$;
                """;

        return configuration -> configuration
                .initSql(sessionSql)
                .callbacks(new BaseCallback() {
                    @Override
                    public boolean supports(Event event, Context context) {
                        return event == Event.BEFORE_VALIDATE
                                || event == Event.BEFORE_MIGRATE
                                || event == Event.BEFORE_EACH_MIGRATE
                                || event == Event.AFTER_MIGRATE;
                    }

                    @Override
                    public void handle(Event event, Context context) {
                        try (Statement statement = context.getConnection().createStatement()) {
                            statement.execute(sessionSql);
                            if (event == Event.BEFORE_EACH_MIGRATE) {
                                statement.execute(disableForceRlsSql);
                            }
                            if (event == Event.AFTER_MIGRATE) {
                                statement.execute(enableForceRlsSql);
                            }
                        } catch (SQLException ex) {
                            throw new IllegalStateException("No fue posible fijar app.current_user_uuid para Flyway", ex);
                        }
                    }
                });
    }
}
