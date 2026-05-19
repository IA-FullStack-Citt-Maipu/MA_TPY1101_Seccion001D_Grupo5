package com.panol_project.backendpanol.modules.catalog.location.application;

import com.panol_project.backendpanol.modules.catalog.location.application.contract.LocationValidationContract;
import com.panol_project.backendpanol.modules.catalog.location.domain.LocationOption;
import com.panol_project.backendpanol.modules.catalog.location.domain.LocationRepository;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService implements LocationValidationContract {

    private final LocationRepository repository;

    public LocationService(LocationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<LocationOption> listarSelector() {
        return repository.findAllActive();
    }

    @Transactional(readOnly = true)
    public List<LocationOption> listarGestion() {
        return repository.findAll();
    }

    @Transactional
    public LocationOption crear(String name, String description) {
        String normalizedName = normalizeRequired(name, "LOCATION_NAME_REQUIRED", "El nombre es obligatorio");
        String normalizedDescription = normalizeOptional(description);
        if (repository.existsByNameIgnoreCase(normalizedName)) {
            throw new BadRequestException("LOCATION_NAME_DUPLICATE", "Ya existe una ubicacion con ese nombre");
        }
        return repository.create(normalizedName, normalizedDescription);
    }

    @Transactional
    public LocationOption editar(UUID uuid, String name, String description) {
        requireLocation(uuid);
        String normalizedName = normalizeRequired(name, "LOCATION_NAME_REQUIRED", "El nombre es obligatorio");
        String normalizedDescription = normalizeOptional(description);
        if (repository.existsByNameIgnoreCaseAndUuidNot(normalizedName, uuid)) {
            throw new BadRequestException("LOCATION_NAME_DUPLICATE", "Ya existe una ubicacion con ese nombre");
        }
        return repository.update(uuid, normalizedName, normalizedDescription);
    }

    @Transactional
    public LocationOption setActive(UUID uuid, boolean active) {
        LocationOption existing = requireLocation(uuid);
        if (Boolean.TRUE.equals(existing.active()) == active) {
            return existing;
        }
        repository.updateActive(uuid, active);
        return requireLocation(uuid);
    }

    @Transactional
    public void eliminar(UUID uuid) {
        LocationOption existing = requireLocation(uuid);
        if (!Boolean.TRUE.equals(existing.active())) {
            return;
        }
        repository.softDelete(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public void validarLocationExistente(UUID locationUuid) {
        if (locationUuid == null) {
            throw new BadRequestException("LOCATION_REQUIRED", "La ubicacion es obligatoria");
        }
        if (!repository.existsByUuid(locationUuid)) {
            throw new BadRequestException("LOCATION_NOT_FOUND", "La ubicacion no existe");
        }
    }

    private LocationOption requireLocation(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("LOCATION_NOT_FOUND", "Ubicacion no encontrada"));
    }

    private String normalizeRequired(String value, String code, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BadRequestException(code, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
