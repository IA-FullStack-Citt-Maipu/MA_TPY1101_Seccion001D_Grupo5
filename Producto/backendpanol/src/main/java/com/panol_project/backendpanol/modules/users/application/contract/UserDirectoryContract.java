package com.panol_project.backendpanol.modules.users.application.contract;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserDirectoryContract {
    Map<UUID, String> getNombresUsuariosByUuid(List<UUID> userUuids);
}
