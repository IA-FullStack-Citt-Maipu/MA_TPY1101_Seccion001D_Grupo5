package com.panol_project.backendpanol.modules.catalog.implement.application;

import com.panol_project.backendpanol.modules.catalog.category.application.contract.CategoryValidationContract;
import com.panol_project.backendpanol.modules.catalog.implement.application.contract.ImplementLookupContract;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementItemType;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementRepository;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementSummary;
import com.panol_project.backendpanol.modules.catalog.implement.domain.Implemento;
import com.panol_project.backendpanol.modules.catalog.implement.domain.StockStatusFilter;
import com.panol_project.backendpanol.modules.catalog.location.application.contract.LocationValidationContract;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImplementService implements ImplementLookupContract {

    private final ImplementRepository repository;
    private final CategoryValidationContract categoryValidationContract;
    private final LocationValidationContract locationValidationContract;

    public ImplementService(
            ImplementRepository repository,
            CategoryValidationContract categoryValidationContract,
            LocationValidationContract locationValidationContract
    ) {
        this.repository = repository;
        this.categoryValidationContract = categoryValidationContract;
        this.locationValidationContract = locationValidationContract;
    }

    @Transactional
    public Implemento crear(
            String nombre,
            String descripcion,
            UUID categoriaUuid,
            UUID locationUuid,
            String itemType,
            Integer minStock,
            String barcode,
            String imgUrl,
            String observations
    ) {
        categoryValidationContract.validarCategoriaActivaParaImplemento(categoriaUuid);
        String normalizedName = normalizeNombre(nombre);
        String normalizedDescription = normalizeDescripcion(descripcion);
        String normalizedBarcode = normalizeBarcode(barcode);
        String normalizedImgUrl = normalizeOptional(imgUrl);
        String normalizedObservations = normalizeObservations(observations);
        ImplementItemType normalizedItemType = parseItemType(itemType);
        locationValidationContract.validarLocationExistente(locationUuid);
        validateUniqueActiveNameForCreate(normalizedName, categoriaUuid);

        try {
            Implemento created = repository.create(
                    normalizedName,
                    normalizedDescription,
                    categoriaUuid,
                    locationUuid,
                    normalizedItemType,
                    normalizedBarcode,
                    normalizedImgUrl,
                    normalizedObservations
            );
            repository.updateMinStockByImplementUuid(created.uuid(), minStock);
            return created;
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw duplicateNameException(normalizedName);
            }
            throw ex;
        }
    }

    @Transactional
    public Implemento editar(
            UUID uuid,
            String nombre,
            String descripcion,
            UUID categoriaUuid,
            UUID locationUuid,
            String itemType,
            Integer minStock,
            String barcode,
            String imgUrl,
            String observations
    ) {
        Implemento existing = requireImplement(uuid);
        if (!Boolean.TRUE.equals(existing.activo())) {
            throw new BadRequestException("IMPLEMENT_INACTIVE", "No se puede editar un producto inactivo");
        }
        categoryValidationContract.validarCategoriaActivaParaImplemento(categoriaUuid);
        locationValidationContract.validarLocationExistente(locationUuid);
        String normalizedName = normalizeNombre(nombre);
        String normalizedDescription = normalizeDescripcion(descripcion);
        String normalizedBarcode = normalizeBarcode(barcode);
        String normalizedImgUrl = normalizeOptional(imgUrl);
        String normalizedObservations = normalizeObservations(observations);
        ImplementItemType normalizedItemType = parseItemType(itemType);
        validateUniqueActiveNameForUpdate(normalizedName, categoriaUuid, uuid);

        try {
            Implemento updated = repository.update(
                    uuid,
                    normalizedName,
                    normalizedDescription,
                    categoriaUuid,
                    locationUuid,
                    normalizedItemType,
                    normalizedBarcode,
                    normalizedImgUrl,
                    normalizedObservations
            );
            repository.updateMinStockByImplementUuid(updated.uuid(), minStock);
            return updated;
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw duplicateNameException(normalizedName);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Implemento obtener(UUID uuid) {
        return requireImplement(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public ImplementLookupSummary obtenerImplementoParaStock(UUID implementUuid) {
        Implemento implemento = requireImplement(implementUuid);
        return new ImplementLookupSummary(
                implemento.uuid(),
                implemento.nombre(),
                implemento.barcode(),
                implemento.itemType() == null ? null : implemento.itemType().literal()
        );
    }

    @Transactional
    public Implemento setActive(UUID uuid, boolean active) {
        Implemento existing = requireImplement(uuid);
        if (Boolean.TRUE.equals(existing.activo()) == active) {
            return existing;
        }
        repository.updateActive(uuid, active);
        return requireImplement(uuid);
    }

    @Transactional(readOnly = true)
    public ImplementSummary obtenerSummary(UUID uuid) {
        // Reusa el join que ya existe para el listado para resolver categoria y ubicacion (incluyendo inactivas).
        return repository.findSummaryByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("IMPLEMENT_NOT_FOUND", "Implemento no encontrado"));
    }

    public String resolveDisplayLocation(ImplementSummary summary) {
        if (summary == null || summary.location() == null) {
            return null;
        }

        Integer loaned = summary.stock() == null ? null : summary.stock().loaned();
        if (loaned != null && loaned > 0) {
            return "Prestado";
        }

        return summary.location().name();
    }

    @Transactional(readOnly = true)
    public Integer obtenerStockMinimo(UUID implementUuid) {
        return repository.findMinStockByImplementUuid(implementUuid).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ImplementSummary> listar(String name, UUID categoryUuid, StockStatusFilter stockStatusFilter) {
        return repository.findAllSummaries(
                normalizeFiltroNombre(name),
                categoryUuid,
                stockStatusFilter
        );
    }

    private Implemento requireImplement(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("IMPLEMENT_NOT_FOUND", "Implemento no encontrado"));
    }

    private String normalizeNombre(String nombre) {
        return nombre == null ? "" : nombre.trim();
    }

    private String normalizeDescripcion(String descripcion) {
        return normalizeOptional(descripcion);
    }

    private String normalizeObservations(String observations) {
        return normalizeOptional(observations);
    }

    private String normalizeBarcode(String barcode) {
        return normalizeOptional(barcode);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeFiltroNombre(String nombre) {
        if (nombre == null) {
            return null;
        }
        String normalized = nombre.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private void validateUniqueActiveNameForCreate(String normalizedName, UUID categoryUuid) {
        if (repository.existsActiveByNameIgnoreCase(normalizedName, categoryUuid)) {
            throw duplicateNameException(normalizedName);
        }
    }

    private void validateUniqueActiveNameForUpdate(String normalizedName, UUID categoryUuid, UUID uuid) {
        if (repository.existsActiveByNameIgnoreCaseAndUuidNot(normalizedName, categoryUuid, uuid)) {
            throw duplicateNameException(normalizedName);
        }
    }

    private BadRequestException duplicateNameException(String normalizedName) {
        return new BadRequestException(
                "IMPLEMENT_NAME_DUPLICATE",
                String.format("Ya existe un producto con el nombre '%s'", normalizedName)
        );
    }

    private ImplementItemType parseItemType(String itemType) {
        return ImplementItemType.fromLiteral(itemType)
                .orElseThrow(() -> new BadRequestException(
                        "IMPLEMENT_ITEM_TYPE_INVALID",
                        "El tipo de implemento debe ser fungible o no_fungible"
                ));
    }

    private boolean isUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return "23505".equals(sqlException.getSQLState());
            }
            current = current.getCause();
        }
        return false;
    }
}
