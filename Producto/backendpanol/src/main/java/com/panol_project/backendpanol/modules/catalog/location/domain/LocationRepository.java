package com.panol_project.backendpanol.modules.catalog.location.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository {

    List<LocationOption> findAllActive();

    List<LocationOption> findAll();

    Optional<LocationOption> findByUuid(UUID uuid);

    boolean existsByUuid(UUID uuid);

    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndUuidNot(String name, UUID uuid);

    LocationOption create(String name, String description);

    LocationOption update(UUID uuid, String name, String description);

    int updateActive(UUID uuid, boolean active);

    int softDelete(UUID uuid);
}
