package com.panol_project.backendpanol.modules.users.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.auth.domain.AuditLogPort;
import com.panol_project.backendpanol.modules.users.application.dto.CreateUserCommand;
import com.panol_project.backendpanol.modules.users.domain.UserAdminRepository;
import com.panol_project.backendpanol.modules.users.domain.UserAdminSummary;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserAdminRepository repository;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private OutboxService outboxService;

    @Mock
    private CurrentUserUuidResolver currentUserUuidResolver;

    @Test
    void createUserDebeRegistrarAuditoriaYOutbox() {
        UserAdminService service = new UserAdminService(repository, auditLogPort, outboxService, currentUserUuidResolver);
        CreateUserCommand command = new CreateUserCommand("Ana", "12.345.678-9", "ana@test.cl", "COORDINADOR", "secret");
        Long roleId = 2L;

        when(repository.findRoleId("COORDINADOR")).thenReturn(roleId);
        when(repository.countUsersByRutOrEmail("12345678", "ana@test.cl")).thenReturn(0);

        service.createUser(command);

        verify(repository).createUser(eq("Ana"), eq("12345678"), eq("ana@test.cl"), anyString(), eq(roleId), eq(true));
        verify(auditLogPort).log("user_created", null, null, Map.of("rut", "12345678", "email", "ana@test.cl", "role", "COORDINADOR"));
        verify(outboxService).enqueue("user", null, "UserCreated", null, Map.of("rut", "12345678", "email", "ana@test.cl", "role", "COORDINADOR"));
    }

    @Test
    void listUsersDebeNormalizarRolYRetornarResumen() {
        UserAdminService service = new UserAdminService(repository, auditLogPort, outboxService, currentUserUuidResolver);
        OffsetDateTime now = OffsetDateTime.now();
        when(repository.listUsers()).thenReturn(List.of(
                new UserAdminSummary("u1", "Luis", "111", "luis@test.cl", "rol_coord", true, now)
        ));

        List<UserAdminSummary> result = service.listUsers();

        assertEquals(1, result.size());
        assertEquals("COORDINADOR", result.get(0).role());
    }
}
