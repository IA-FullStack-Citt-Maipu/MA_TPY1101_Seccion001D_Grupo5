package com.panol_project.backendpanol.modules.users.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserRepository {
    Map<UUID, String> findNamesByUuids(List<UUID> uuids);
}
