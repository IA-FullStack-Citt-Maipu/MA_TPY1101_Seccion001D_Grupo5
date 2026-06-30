package com.panol_project.backendpanol.modules.auth.domain;

import java.util.Map;
import java.util.UUID;

public interface AuditLogPort {
    void log(String event, UUID actorUserUuid, UUID targetUserUuid, Map<String, Object> payload);
}
