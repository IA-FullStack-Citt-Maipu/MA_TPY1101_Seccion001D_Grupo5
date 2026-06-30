package com.panol_project.backendpanol.modules.users.api;

import com.panol_project.backendpanol.modules.users.api.dto.ChangeRoleRequest;
import com.panol_project.backendpanol.modules.users.api.dto.CreateUserRequest;
import com.panol_project.backendpanol.modules.users.api.dto.UpdateUserRequest;
import com.panol_project.backendpanol.modules.users.api.dto.UserAdminSummaryResponse;
import com.panol_project.backendpanol.modules.users.application.UserAdminService;
import com.panol_project.backendpanol.modules.users.application.dto.CreateUserCommand;
import com.panol_project.backendpanol.modules.users.application.dto.UpdateUserCommand;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/users")
public class UserAdminV2Controller {

    private final UserAdminService userAdminService;

    public UserAdminV2Controller(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    List<UserAdminSummaryResponse> listUsers() {
        return userAdminService.listUsers().stream()
                .map(row -> new UserAdminSummaryResponse(
                        row.uuid(),
                        row.name(),
                        row.rut(),
                        row.email(),
                        row.role(),
                        row.active(),
                        row.createdAt()))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    ResponseEntity<Void> createUser(@Valid @RequestBody CreateUserRequest request) {
        userAdminService.createUser(
                new CreateUserCommand(
                        request.name(),
                        request.rut(),
                        request.email(),
                        request.role(),
                        request.password()
                )
        );
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/{userUuid}/role")
    @PreAuthorize("hasRole('DIRECTOR')")
    ResponseEntity<Void> changeRole(
            @PathVariable UUID userUuid,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        userAdminService.changeRole(userUuid, request.role());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userUuid}")
    @PreAuthorize("hasRole('DIRECTOR')")
    ResponseEntity<Void> updateUser(
            @PathVariable UUID userUuid,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        userAdminService.updateUser(
                userUuid,
                new UpdateUserCommand(request.name(), request.rut(), request.email())
        );
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userUuid}/active")
    @PreAuthorize("hasRole('DIRECTOR')")
    ResponseEntity<Void> setActive(
            @PathVariable UUID userUuid,
            @RequestParam boolean active
    ) {
        userAdminService.setActive(userUuid, active);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userUuid}")
    @PreAuthorize("hasRole('DIRECTOR')")
    ResponseEntity<Void> deleteUser(@PathVariable UUID userUuid) {
        userAdminService.deleteUser(userUuid);
        return ResponseEntity.noContent().build();
    }
}
