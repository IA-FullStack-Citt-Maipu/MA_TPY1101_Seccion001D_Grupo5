package com.panol_project.backendpanol.bootstrap.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("cloudsql")
@ConditionalOnProperty(name = "app.db.cloudsql.pg16-no-force-rls-enabled", havingValue = "true")
public class CloudSqlPg16RlsCompatibilityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudSqlPg16RlsCompatibilityConfig.class);

    private static final String DISABLE_FORCE_RLS_SQL = """
            DO $$
            BEGIN
                IF current_setting('server_version_num')::int < 170000 THEN
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
                END IF;
            END
            $$;
            """;

    @Bean
    ApplicationRunner cloudSqlPg16RlsCompatibilityRunner(DataSource dataSource) {
        return args -> {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                int serverVersionNum = resolveServerVersionNum(statement);
                if (serverVersionNum >= 170000) {
                    LOGGER.info("Cloud SQL RLS compatibility no requerida para server_version_num={}", serverVersionNum);
                    return;
                }

                statement.execute(DISABLE_FORCE_RLS_SQL);
                LOGGER.warn("Aplicado hotfix PG16 NO FORCE RLS para Cloud SQL en server_version_num={}", serverVersionNum);
            } catch (SQLException ex) {
                throw new IllegalStateException("No fue posible aplicar el hotfix Cloud SQL PG16 NO FORCE RLS", ex);
            }
        };
    }

    private int resolveServerVersionNum(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("select current_setting('server_version_num')::int")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("No fue posible resolver server_version_num");
            }
            return resultSet.getInt(1);
        }
    }
}
