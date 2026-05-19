package com.panol_project.backendpanol.modules.catalog.category.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaRepository {

    List<Categoria> findAll(boolean includeInactive);

    Optional<Categoria> findByUuid(UUID uuid);

    boolean existsByNombre(String nombre, UUID excludingUuid);

    Optional<Categoria> findActiveByUuid(UUID uuid);

    Categoria create(String nombre, String descripcion);

    Categoria updateNombre(UUID uuid, String nombre, String descripcion);

    void deactivate(UUID uuid);

    void activate(UUID uuid);

    void deleteByUuid(UUID uuid);

    int countImplementsByCategoryUuid(UUID categoryUuid);

    int countActiveImplementsByCategoryUuid(UUID categoryUuid);
}
