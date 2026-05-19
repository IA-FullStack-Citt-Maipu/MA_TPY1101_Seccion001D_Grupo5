package com.panol_project.backendpanol.shared.persistence.rls;

import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.jooq.ExecuteContext;
import org.jooq.impl.DefaultExecuteListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AppCurrentUserRlsExecuteListener extends DefaultExecuteListener {

    private static final Logger LOG = LoggerFactory.getLogger(AppCurrentUserRlsExecuteListener.class);
    private final CurrentUserUuidResolver currentUserUuidResolver;

    public AppCurrentUserRlsExecuteListener(CurrentUserUuidResolver currentUserUuidResolver) {
        this.currentUserUuidResolver = currentUserUuidResolver;
    }

    @Override
    public void executeStart(ExecuteContext ctx) {
        UUID userUuid = currentUserUuidResolver.resolveCurrentUserUuid().orElse(null);

        if (userUuid == null) {
            return;
        }

        String sql = "SELECT set_config('app.current_user_uuid', ?, true)";

        // Al usar la conexión JDBC nativa (ctx.connection()), jOOQ no intercepta
        // esta ejecución intermedia y se evita la recursión del listener.
        try (PreparedStatement stmt = ctx.connection().prepareStatement(sql)) {
            stmt.setString(1, userUuid.toString());
            stmt.execute();
        } catch (SQLException e) {
            LOG.error("Error crítico inyectando el UUID transaccional en el contexto RLS", e);
        }
    }

}
