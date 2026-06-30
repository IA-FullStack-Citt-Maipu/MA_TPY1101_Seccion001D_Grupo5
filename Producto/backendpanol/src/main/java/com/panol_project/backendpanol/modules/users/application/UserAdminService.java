package com.panol_project.backendpanol.modules.users.application;

import com.panol_project.backendpanol.modules.auth.domain.AuditLogPort;
import com.panol_project.backendpanol.modules.users.application.dto.CreateUserCommand;
import com.panol_project.backendpanol.modules.users.application.dto.UpdateUserCommand;
import com.panol_project.backendpanol.modules.users.domain.UserAdminManagedUser;
import com.panol_project.backendpanol.modules.users.domain.UserAdminRepository;
import com.panol_project.backendpanol.modules.users.domain.UserAdminSummary;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private static final Set<String> ALLOWED_ROLES = Set.of("DIRECTOR", "COORDINADOR", "DOCENTE");
    private static final String DEFAULT_SYSTEM_USER_UUID = "99999999-9999-9999-9999-999999999999";

    private final UserAdminRepository repository;
    private final AuditLogPort auditLogPort;
    private final OutboxService outboxService;
    private final CurrentUserUuidResolver currentUserUuidResolver;
    private final UUID systemUserUuid;

    public UserAdminService(
            UserAdminRepository repository,
            AuditLogPort auditLogPort,
            OutboxService outboxService,
            CurrentUserUuidResolver currentUserUuidResolver,
            @Value("${app.outbox.system-user-uuid:" + DEFAULT_SYSTEM_USER_UUID + "}") String systemUserUuidRaw
    ) {
        this.repository = repository;
        this.auditLogPort = auditLogPort;
        this.outboxService = outboxService;
        this.currentUserUuidResolver = currentUserUuidResolver;
        this.systemUserUuid = parseSystemUserUuid(systemUserUuidRaw);
    }

    @Transactional
    public void createUser(CreateUserCommand command) {
        UUID actorUuid = resolveActorUuid();
        String role = normalizeRole(command.role());
        String normalizedRut = normalizeRut(command.rut());
        String normalizedEmail = normalizeEmail(command.email());
        Long roleId = repository.findRoleId(role);
        if (roleId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_SUPPORTED", "Rol invalido");
        }

        if (repository.countUsersByRutOrEmail(normalizedRut, normalizedEmail) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_DUPLICATED", "No fue posible procesar la solicitud");
        }

        repository.createUser(
                command.name().trim(),
                normalizedRut,
                normalizedEmail,
                BCrypt.hashpw(command.password(), BCrypt.gensalt()),
                roleId,
                true
        );

        auditLogPort.log("user_created", actorUuid, null, Map.of("rut", normalizedRut, "email", normalizedEmail, "role", role));
        outboxService.enqueue("user", null, "UserCreated", actorUuid, Map.of("rut", normalizedRut, "email", normalizedEmail, "role", role));
    }

    @Transactional
    public void changeRole(UUID userUuid, String roleInput) {
        UUID actorUuid = resolveActorUuid();
        String role = normalizeRole(roleInput);
        Long roleId = repository.findRoleId(role);
        if (roleId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_SUPPORTED", "Rol invalido");
        }
        int updated = repository.updateUserRole(userUuid, roleId);

        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado");
        }

        auditLogPort.log("user_role_changed", actorUuid, userUuid, Map.of("new_role", role));
        outboxService.enqueue("user", userUuid, "UserRoleChanged", actorUuid, Map.of("new_role", role));
    }

    @Transactional(readOnly = true)
    public List<UserAdminSummary> listUsers() {
        return repository.listUsers().stream()
                .map(row -> new UserAdminSummary(
                        row.uuid(),
                        row.name(),
                        row.rut(),
                        row.email(),
                        normalizeRoleForResponse(row.role()),
                        row.active(),
                        row.createdAt()))
                .toList();
    }

    @Transactional
    public void setActive(UUID userUuid, boolean active) {
        UUID actorUuid = resolveActorUuid();
        UserAdminManagedUser targetUser = repository.findManagedUserByUuid(userUuid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado"));

        if (!active && actorUuid != null && actorUuid.equals(targetUser.uuid())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_SELF_DEACTIVATION_NOT_ALLOWED", "No puedes desactivar tu propio usuario");
        }
        if (!active && systemUserUuid.equals(targetUser.uuid())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_SYSTEM_DEACTIVATION_NOT_ALLOWED", "No puedes desactivar el usuario tecnico del sistema");
        }
        if (targetUser.active() == active) {
            return;
        }

        int updated = repository.updateUserActive(targetUser.uuid(), active);

        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado");
        }

        auditLogPort.log(
                active ? "user_activated" : "user_deactivated",
                actorUuid,
                userUuid,
                Map.of("active", active));
        outboxService.enqueue("user", userUuid, active ? "UserActivated" : "UserDeactivated", actorUuid, Map.of("active", active));
    }

    @Transactional
    public void updateUser(UUID userUuid, UpdateUserCommand command) {
        if (!existsUserByUuid(userUuid)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado");
        }
        UUID actorUuid = resolveActorUuid();
        String normalizedRut = normalizeRut(command.rut());
        String normalizedEmail = normalizeEmail(command.email());

        if (repository.countUsersByRutOrEmailExcludingUser(normalizedRut, normalizedEmail, userUuid) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_DUPLICATED", "No fue posible procesar la solicitud");
        }

        int updated = repository.updateUser(userUuid, command.name().trim(), normalizedRut, normalizedEmail);

        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado");
        }

        auditLogPort.log("user_updated", actorUuid, userUuid, Map.of("rut", normalizedRut, "email", normalizedEmail));
        outboxService.enqueue("user", userUuid, "UserUpdated", actorUuid, Map.of("rut", normalizedRut, "email", normalizedEmail));
    }

    @Transactional
    public void deleteUser(UUID userUuid) {
        UUID actorUuid = resolveActorUuid();
        UserAdminManagedUser targetUser = repository.findManagedUserByUuid(userUuid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado"));

        if (actorUuid != null && actorUuid.equals(targetUser.uuid())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_SELF_DELETION_NOT_ALLOWED", "No puedes eliminar tu propio usuario");
        }
        if (systemUserUuid.equals(targetUser.uuid())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_SYSTEM_DELETION_NOT_ALLOWED", "No puedes eliminar el usuario tecnico del sistema");
        }
        if (targetUser.active()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_DELETE_REQUIRES_INACTIVE", "Debes desactivar el usuario antes de eliminarlo");
        }
        if (repository.hasBlockingReferences(targetUser.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_DELETE_NOT_ALLOWED", "No se puede eliminar el usuario porque tiene historial asociado");
        }

        int deleted = repository.deleteUserByUuid(targetUser.uuid());
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado");
        }

        Map<String, Object> payload = Map.of(
                "deleted_user_uuid", targetUser.uuid().toString(),
                "deleted_user_name", targetUser.name()
        );
        auditLogPort.log("user_deleted", actorUuid, null, payload);
        outboxService.enqueue("user", targetUser.uuid(), "UserDeleted", actorUuid, payload);
    }

    private String normalizeRole(String roleRaw) {
        String role = roleRaw == null ? "" : roleRaw.trim().toUpperCase();
        if (role.contains("DIRECTOR")) role = "DIRECTOR";
        else if (role.contains("COORD")) role = "COORDINADOR";
        else if (role.contains("DOCENTE")) role = "DOCENTE";
        if (!ALLOWED_ROLES.contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_SUPPORTED", "Rol invalido");
        }
        return role;
    }

    private String normalizeRoleForResponse(String roleRaw) {
        if (roleRaw == null || roleRaw.isBlank()) {
            return "DOCENTE";
        }
        String role = roleRaw.trim().toUpperCase();
        if (role.contains("DIRECTOR")) return "DIRECTOR";
        if (role.contains("COORD")) return "COORDINADOR";
        if (role.contains("DOCENTE")) return "DOCENTE";
        return role;
    }

    private String normalizeRut(String rutRaw) {
        String compactRut = rutRaw == null ? "" : rutRaw.replaceAll("[.\\-\\s]", "").trim().toUpperCase();
        if (compactRut.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_RUT_REQUIRED", "El RUT es obligatorio");
        }
        if (compactRut.length() < 8 || compactRut.length() > 9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_RUT_INVALID", "El RUT no tiene formato valido");
        }
        if (!compactRut.chars().allMatch(ch -> Character.isDigit(ch) || ch == 'K')) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_RUT_INVALID", "El RUT no tiene formato valido");
        }

        String rutBody = compactRut.substring(0, compactRut.length() - 1);
        char verifier = compactRut.charAt(compactRut.length() - 1);
        if (!rutBody.chars().allMatch(Character::isDigit) || (!Character.isDigit(verifier) && verifier != 'K')) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_RUT_INVALID", "El RUT no tiene formato valido");
        }
        return compactRut;
    }

    private String normalizeEmail(String emailRaw) {
        if (emailRaw == null || emailRaw.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_EMAIL_REQUIRED", "El correo es obligatorio");
        }
        return emailRaw.trim().toLowerCase();
    }

    private boolean existsUserByUuid(UUID uuid) {
        return repository.existsUserByUuid(uuid);
    }

    private UUID resolveActorUuid() {
        return currentUserUuidResolver.resolveCurrentUserUuid().orElse(null);
    }

    private UUID parseSystemUserUuid(String rawUuid) {
        try {
            return UUID.fromString(rawUuid == null || rawUuid.isBlank() ? DEFAULT_SYSTEM_USER_UUID : rawUuid.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.outbox.system-user-uuid no tiene un UUID valido", ex);
        }
    }
}
