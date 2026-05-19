package com.panol_project.backendpanol.modules.catalog.category.application;

import com.panol_project.backendpanol.modules.catalog.category.application.contract.CategoryValidationContract;
import com.panol_project.backendpanol.modules.catalog.category.domain.Categoria;
import com.panol_project.backendpanol.modules.catalog.category.domain.CategoriaRepository;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.ConflictException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.jooq.exception.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaService implements CategoryValidationContract {

    private final CategoriaRepository repository;

    public CategoriaService(CategoriaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Categoria> listarGestion() {
        return repository.findAll(true);
    }

    @Transactional(readOnly = true)
    public List<Categoria> listarSelector() {
        return repository.findAll(false);
    }

    @Transactional
    public Categoria crear(String nombre, String descripcion) {
        String normalizedName = normalizeNombre(nombre);
        String normalizedDescription = normalizeDescripcion(descripcion);
        validateNombreUnico(normalizedName, null);

        try {
            return repository.create(normalizedName, normalizedDescription);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw duplicateNameError(normalizedName);
            }
            throw ex;
        }
    }

    @Transactional
    public Categoria editar(UUID uuid, String nombre, String descripcion) {
        Categoria categoria = requireCategoria(uuid);
        String normalizedName = normalizeNombre(nombre);
        String normalizedDescription = normalizeDescripcion(descripcion);

        validateNombreUnico(normalizedName, categoria.uuid());

        try {
            return repository.updateNombre(uuid, normalizedName, normalizedDescription);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw duplicateNameError(normalizedName);
            }
            throw ex;
        }
    }

    @Transactional
    public Categoria desactivar(UUID uuid, boolean force) {
        Categoria categoria = requireCategoria(uuid);
        if (!Boolean.TRUE.equals(categoria.activa())) {
            return categoria;
        }

        int activeImplements = repository.countActiveImplementsByCategoryUuid(uuid);
        if (activeImplements > 0 && !force) {
            throw new ConflictException(
                    "CATEGORY_HAS_ACTIVE_IMPLEMENTS",
                    "La categoria tiene " + activeImplements + " implementos activos asociados"
            );
        }

        repository.deactivate(uuid);
        return new Categoria(
                categoria.uuid(),
                categoria.nombre(),
                categoria.descripcion(),
                false,
                categoria.createdAt()
        );
    }

    @Transactional
    public Categoria activar(UUID uuid) {
        Categoria categoria = requireCategoria(uuid);
        if (Boolean.TRUE.equals(categoria.activa())) {
            return categoria;
        }

        try {
            repository.activate(uuid);
        } catch (DataAccessException ex) {
            if (isCategoryNameActiveConflict(ex)) {
                throw new BadRequestException(
                        "CATEGORY_NAME_ACTIVE_DUPLICATE",
                        "Ya existe una categoría activa con ese nombre."
                );
            }
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            if (isCategoryNameActiveConflict(ex)) {
                throw new BadRequestException(
                        "CATEGORY_NAME_ACTIVE_DUPLICATE",
                        "Ya existe una categoría activa con ese nombre."
                );
            }
            throw ex;
        }
        return new Categoria(
                categoria.uuid(),
                categoria.nombre(),
                categoria.descripcion(),
                true,
                categoria.createdAt()
        );
    }

    @Transactional
    public void eliminar(UUID uuid) {
        requireCategoria(uuid);

        int totalImplements = repository.countImplementsByCategoryUuid(uuid);
        if (totalImplements > 0) {
            throw new BadRequestException(
                    "CATEGORY_HAS_IMPLEMENTS",
                    "No se puede eliminar la categoria porque tiene " + totalImplements + " implementos asociados"
            );
        }

        repository.deleteByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public void validarCategoriaActivaParaImplemento(UUID categoryUuid) {
        if (categoryUuid == null) {
            return;
        }

        boolean existsActive = repository.findActiveByUuid(categoryUuid).isPresent();
        if (!existsActive) {
            throw new BadRequestException(
                    "CATEGORY_INACTIVE_OR_NOT_FOUND",
                    "No se puede asignar una categoria inactiva al implemento"
            );
        }
    }

    @Transactional(readOnly = true)
    public int contarImplementsAsociados(UUID uuid) {
        requireCategoria(uuid);
        return repository.countImplementsByCategoryUuid(uuid);
    }

    private void validateNombreUnico(String nombre, UUID excludingUuid) {
        if (repository.existsByNombre(nombre, excludingUuid)) {
            throw duplicateNameError(nombre);
        }
    }

    private Categoria requireCategoria(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Categoria no encontrada"));
    }

    private String normalizeNombre(String nombre) {
        return nombre == null ? "" : nombre.trim();
    }

    private String normalizeDescripcion(String descripcion) {
        if (descripcion == null) {
            return null;
        }
        String normalized = descripcion.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private BadRequestException duplicateNameError(String nombre) {
        return new BadRequestException(
                "CATEGORY_NAME_DUPLICATE",
                "Ya existe una categoria con el nombre '" + nombre + "'"
        );
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

    private boolean isCategoryNameActiveConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (containsIgnoreCase(current.getMessage(), "category_name_key")
                    || containsIgnoreCase(current.getMessage(), "duplicate key value violates unique constraint \"category_name_key\"")) {
                return true;
            }

            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())
                    && containsIgnoreCase(sqlException.getMessage(), "category_name_key")) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String needle) {
        if (text == null || needle == null) {
            return false;
        }
        return text.toLowerCase().contains(needle.toLowerCase());
    }
}

