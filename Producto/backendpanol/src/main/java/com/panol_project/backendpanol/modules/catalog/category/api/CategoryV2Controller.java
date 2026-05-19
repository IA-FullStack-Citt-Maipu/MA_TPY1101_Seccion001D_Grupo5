package com.panol_project.backendpanol.modules.catalog.category.api;

import com.panol_project.backendpanol.modules.catalog.category.api.dto.CategoryAssociationV2Response;
import com.panol_project.backendpanol.modules.catalog.category.api.dto.CategoryActiveV2Response;
import com.panol_project.backendpanol.modules.catalog.category.api.dto.CategoryManagementV2Response;
import com.panol_project.backendpanol.modules.catalog.category.api.dto.CreateCategoriaRequest;
import com.panol_project.backendpanol.modules.catalog.category.api.dto.UpdateCategoriaRequest;
import com.panol_project.backendpanol.modules.catalog.category.application.CategoriaService;
import com.panol_project.backendpanol.modules.catalog.category.domain.Categoria;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/categories")
public class CategoryV2Controller {

    private final CategoriaService categoriaService;

    public CategoryV2Controller(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('COORDINADOR')")
    public List<CategoryActiveV2Response> listActive() {
        var categories = categoriaService.listarSelector();
        return categories.stream()
                .map(category -> new CategoryActiveV2Response(category.uuid(), category.nombre()))
                .toList();
    }

    @GetMapping("/gestion")
    @PreAuthorize("hasRole('COORDINADOR')")
    public List<CategoryManagementV2Response> listManagement() {
        var categories = categoriaService.listarGestion();
        return categories.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{categoryUuid}/associations")
    @PreAuthorize("hasRole('COORDINADOR')")
    public CategoryAssociationV2Response associations(@PathVariable UUID categoryUuid) {
        int implementCount = categoriaService.contarImplementsAsociados(categoryUuid);
        return new CategoryAssociationV2Response(categoryUuid, implementCount, implementCount == 0);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COORDINADOR')")
    public CategoryManagementV2Response create(@Valid @RequestBody CreateCategoriaRequest request) {
        var created = categoriaService.crear(request.nombre(), request.descripcion());
        return toResponse(created);
    }

    @PutMapping("/{categoryUuid}")
    @PreAuthorize("hasRole('COORDINADOR')")
    public CategoryManagementV2Response update(@PathVariable UUID categoryUuid, @Valid @RequestBody UpdateCategoriaRequest request) {
        var updated = categoriaService.editar(categoryUuid, request.nombre(), request.descripcion());
        return toResponse(updated);
    }

    @PatchMapping("/{categoryUuid}/deactivate")
    @PreAuthorize("hasRole('COORDINADOR')")
    public CategoryManagementV2Response deactivate(@PathVariable UUID categoryUuid, @RequestParam(defaultValue = "false") boolean force) {
        var updated = categoriaService.desactivar(categoryUuid, force);
        return toResponse(updated);
    }

    @PatchMapping("/{categoryUuid}/activate")
    @PreAuthorize("hasRole('COORDINADOR')")
    public CategoryManagementV2Response activate(@PathVariable UUID categoryUuid) {
        var updated = categoriaService.activar(categoryUuid);
        return toResponse(updated);
    }

    @DeleteMapping("/{categoryUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COORDINADOR')")
    public void delete(@PathVariable UUID categoryUuid) {
        categoriaService.eliminar(categoryUuid);
    }

    private CategoryManagementV2Response toResponse(Categoria category) {
        return new CategoryManagementV2Response(category.uuid(), category.nombre(), category.descripcion(), category.activa(), category.createdAt());
    }
}
