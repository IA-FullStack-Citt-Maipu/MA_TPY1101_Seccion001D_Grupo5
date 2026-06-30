package com.panol_project.backendpanol.modules.users.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.auth.domain.AuditLogPort;
import com.panol_project.backendpanol.modules.users.application.dto.CreateUserCommand;
import com.panol_project.backendpanol.modules.users.application.dto.UpdateUserCommand;
import com.panol_project.backendpanol.modules.users.domain.UserAdminManagedUser;
import com.panol_project.backendpanol.modules.users.domain.UserAdminRepository;
import com.panol_project.backendpanol.modules.users.domain.UserAdminSummary;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    private static final String DEFAULT_SYSTEM_USER_UUID = "99999999-9999-9999-9999-999999999999";

    @Mock
    private UserAdminRepository repository;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private OutboxService outboxService;

    @Mock
    private CurrentUserUuidResolver currentUserUuidResolver;

    private UserAdminService createService() {
        return new UserAdminService(
                repository,
                auditLogPort,
                outboxService,
                currentUserUuidResolver,
                DEFAULT_SYSTEM_USER_UUID
        );
    }

    @Test
    void createUserDebeRegistrarAuditoriaYOutbox() {
        UserAdminService service = createService();
        CreateUserCommand command = new CreateUserCommand("Ana", "12.345.678-9", "ana@test.cl", "COORDINADOR", "secret");
        Long roleId = 2L;

        when(repository.findRoleId("COORDINADOR")).thenReturn(roleId);
        when(repository.countUsersByRutOrEmail("123456789", "ana@test.cl")).thenReturn(0);

        service.createUser(command);

        verify(repository).createUser(eq("Ana"), eq("123456789"), eq("ana@test.cl"), anyString(), eq(roleId), eq(true));
        verify(auditLogPort).log("user_created", null, null, Map.of("rut", "123456789", "email", "ana@test.cl", "role", "COORDINADOR"));
        verify(outboxService).enqueue("user", null, "UserCreated", null, Map.of("rut", "123456789", "email", "ana@test.cl", "role", "COORDINADOR"));
    }

    @Test
    void createUserDebeAceptarDigitoVerificadorK() {
        UserAdminService service = createService();
        CreateUserCommand command = new CreateUserCommand("Karen", "12.345.678-k", "karen@test.cl", "DOCENTE", "secret");
        Long roleId = 3L;

        when(repository.findRoleId("DOCENTE")).thenReturn(roleId);
        when(repository.countUsersByRutOrEmail("12345678K", "karen@test.cl")).thenReturn(0);

        service.createUser(command);

        verify(repository).createUser(eq("Karen"), eq("12345678K"), eq("karen@test.cl"), anyString(), eq(roleId), eq(true));
    }

    @Test
    void updateUserDebePersistirRutCompletoConDigitoVerificador() {
        UserAdminService service = createService();
        UUID userUuid = UUID.randomUUID();

        when(repository.existsUserByUuid(userUuid)).thenReturn(true);
        when(repository.countUsersByRutOrEmailExcludingUser("123456789", "ana.actualizada@test.cl", userUuid)).thenReturn(0);
        when(repository.updateUser(userUuid, "Ana Actualizada", "123456789", "ana.actualizada@test.cl")).thenReturn(1);

        service.updateUser(userUuid, new UpdateUserCommand("Ana Actualizada", "12.345.678-9", "ana.actualizada@test.cl"));

        verify(repository).updateUser(userUuid, "Ana Actualizada", "123456789", "ana.actualizada@test.cl");
        verify(auditLogPort).log("user_updated", null, userUuid, Map.of("rut", "123456789", "email", "ana.actualizada@test.cl"));
        verify(outboxService).enqueue("user", userUuid, "UserUpdated", null, Map.of("rut", "123456789", "email", "ana.actualizada@test.cl"));
    }

    @Test
    void createUserDebeRechazarRutSinDigitoVerificador() {
        UserAdminService service = createService();

        ApiException error = assertThrows(ApiException.class, () ->
                service.createUser(new CreateUserCommand("Ana", "1.234.567", "ana@test.cl", "COORDINADOR", "secret")));

        assertEquals("USER_RUT_INVALID", error.getCode());
    }

    @Test
    void createUserDebeRechazarRutConCaracteresInvalidos() {
        UserAdminService service = createService();

        ApiException error = assertThrows(ApiException.class, () ->
                service.createUser(new CreateUserCommand("Ana", "12.345.67A-9", "ana@test.cl", "COORDINADOR", "secret")));

        assertEquals("USER_RUT_INVALID", error.getCode());
    }

    @Test
    void createUserDebeRechazarRutFueraDeRango() {
        UserAdminService service = createService();

        ApiException error = assertThrows(ApiException.class, () ->
                service.createUser(new CreateUserCommand("Ana", "123456-7", "ana@test.cl", "COORDINADOR", "secret")));

        assertEquals("USER_RUT_INVALID", error.getCode());
    }

    @Test
    void listUsersDebeNormalizarRolYRetornarResumen() {
        UserAdminService service = createService();
        OffsetDateTime now = OffsetDateTime.now();
        when(repository.listUsers()).thenReturn(List.of(
                new UserAdminSummary("u1", "Luis", "111", "luis@test.cl", "rol_coord", true, now)
        ));

        List<UserAdminSummary> result = service.listUsers();

        assertEquals(1, result.size());
        assertEquals("COORDINADOR", result.get(0).role());
    }

    @Test
    void setActiveDebeDesactivarUsuarioActivo() {
        UserAdminService service = createService();
        UUID actorUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();

        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));
        when(repository.findManagedUserByUuid(userUuid)).thenReturn(Optional.of(new UserAdminManagedUser(10L, userUuid, "Lucas", true)));
        when(repository.updateUserActive(userUuid, false)).thenReturn(1);

        service.setActive(userUuid, false);

        verify(repository).updateUserActive(userUuid, false);
        verify(auditLogPort).log("user_deactivated", actorUuid, userUuid, Map.of("active", false));
        verify(outboxService).enqueue("user", userUuid, "UserDeactivated", actorUuid, Map.of("active", false));
    }

    @Test
    void setActiveDebeReactivarUsuarioInactivo() {
        UserAdminService service = createService();
        UUID actorUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();

        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));
        when(repository.findManagedUserByUuid(userUuid)).thenReturn(Optional.of(new UserAdminManagedUser(11L, userUuid, "Lucas", false)));
        when(repository.updateUserActive(userUuid, true)).thenReturn(1);

        service.setActive(userUuid, true);

        verify(repository).updateUserActive(userUuid, true);
        verify(auditLogPort).log("user_activated", actorUuid, userUuid, Map.of("active", true));
        verify(outboxService).enqueue("user", userUuid, "UserActivated", actorUuid, Map.of("active", true));
    }

    @Test
    void setActiveDebeImpedirDesactivarPropioUsuario() {
        UserAdminService service = createService();
        UUID actorUuid = UUID.randomUUID();

        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));
        when(repository.findManagedUserByUuid(actorUuid)).thenReturn(Optional.of(new UserAdminManagedUser(12L, actorUuid, "Director", true)));

        ApiException error = assertThrows(ApiException.class, () -> service.setActive(actorUuid, false));

        assertEquals("USER_SELF_DEACTIVATION_NOT_ALLOWED", error.getCode());
        verify(repository, never()).updateUserActive(any(), eq(false));
    }

    @Test
    void deleteUserDebeEliminarUsuarioInactivoSinReferencias() {
        UserAdminService service = createService();
        UUID actorUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UserAdminManagedUser targetUser = new UserAdminManagedUser(20L, userUuid, "Usuario Borrable", false);

        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));
        when(repository.findManagedUserByUuid(userUuid)).thenReturn(Optional.of(targetUser));
        when(repository.hasBlockingReferences(20L)).thenReturn(false);
        when(repository.deleteUserByUuid(userUuid)).thenReturn(1);

        service.deleteUser(userUuid);

        verify(repository).deleteUserByUuid(userUuid);
        verify(auditLogPort).log("user_deleted", actorUuid, null, Map.of(
                "deleted_user_uuid", userUuid.toString(),
                "deleted_user_name", "Usuario Borrable"
        ));
        verify(outboxService).enqueue("user", userUuid, "UserDeleted", actorUuid, Map.of(
                "deleted_user_uuid", userUuid.toString(),
                "deleted_user_name", "Usuario Borrable"
        ));
    }

    @Test
    void deleteUserDebeRechazarUsuarioActivo() {
        UserAdminService service = createService();
        UUID actorUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();

        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));
        when(repository.findManagedUserByUuid(userUuid)).thenReturn(Optional.of(new UserAdminManagedUser(21L, userUuid, "Usuario Activo", true)));

        ApiException error = assertThrows(ApiException.class, () -> service.deleteUser(userUuid));

        assertEquals("USER_DELETE_REQUIRES_INACTIVE", error.getCode());
        verify(repository, never()).deleteUserByUuid(any());
    }

    @Test
    void deleteUserDebeRechazarUsuarioConReferenciasBloqueantes() {
        UserAdminService service = createService();
        UUID actorUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();

        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));
        when(repository.findManagedUserByUuid(userUuid)).thenReturn(Optional.of(new UserAdminManagedUser(22L, userUuid, "Usuario Historico", false)));
        when(repository.hasBlockingReferences(22L)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () -> service.deleteUser(userUuid));

        assertEquals("USER_DELETE_NOT_ALLOWED", error.getCode());
        verify(repository, never()).deleteUserByUuid(any());
    }

    @Test
    void deleteUserDebeImpedirEliminarPropioUsuario() {
        UserAdminService service = createService();
        UUID actorUuid = UUID.randomUUID();

        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));
        when(repository.findManagedUserByUuid(actorUuid)).thenReturn(Optional.of(new UserAdminManagedUser(23L, actorUuid, "Director", false)));

        ApiException error = assertThrows(ApiException.class, () -> service.deleteUser(actorUuid));

        assertEquals("USER_SELF_DELETION_NOT_ALLOWED", error.getCode());
        verify(repository, never()).deleteUserByUuid(any());
    }

    @Test
    void deleteUserDebeImpedirEliminarUsuarioTecnico() {
        UserAdminService service = createService();
        UUID actorUuid = UUID.randomUUID();
        UUID systemUserUuid = UUID.fromString("99999999-9999-9999-9999-999999999999");

        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));
        when(repository.findManagedUserByUuid(systemUserUuid)).thenReturn(Optional.of(new UserAdminManagedUser(24L, systemUserUuid, "SISTEMA_OUTBOX", false)));

        ApiException error = assertThrows(ApiException.class, () -> service.deleteUser(systemUserUuid));

        assertEquals("USER_SYSTEM_DELETION_NOT_ALLOWED", error.getCode());
        verify(repository, never()).deleteUserByUuid(any());
    }
}
