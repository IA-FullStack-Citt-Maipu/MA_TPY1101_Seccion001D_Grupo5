package com.panol_project.backendpanol.modules.catalog.implement.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.category.application.contract.CategoryValidationContract;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementItemType;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementRepository;
import com.panol_project.backendpanol.modules.catalog.implement.domain.Implemento;
import com.panol_project.backendpanol.modules.catalog.implement.domain.StockStatusFilter;
import com.panol_project.backendpanol.modules.catalog.location.application.contract.LocationValidationContract;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ImplementServiceTest {

    @Mock
    private ImplementRepository repository;

    @Mock
    private CategoryValidationContract categoryValidationContract;

    @Mock
    private LocationValidationContract locationValidationContract;

    private ImplementService service;

    @BeforeEach
    void setUp() {
        service = new ImplementService(repository, categoryValidationContract, locationValidationContract);
    }

    @Test
    void crearDebeValidarLocationYActualizarStockMinimo() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID implementUuid = UUID.randomUUID();
        UUID categoryUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        Implemento created = new Implemento(implementUuid, "Guantes", null, categoryUuid, locationUuid, ImplementItemType.NO_FUNGIBLE, null, null, null, true, now, now);

        when(repository.create("Guantes", null, categoryUuid, locationUuid, ImplementItemType.NO_FUNGIBLE, null, null, null)).thenReturn(created);
        when(repository.updateMinStockByImplementUuid(implementUuid, 3)).thenReturn(1);

        Implemento result = service.crear("Guantes", null, categoryUuid, locationUuid, "no_fungible", 3, " ", null, null);

        assertEquals(implementUuid, result.uuid());
        verify(categoryValidationContract).validarCategoriaActivaParaImplemento(categoryUuid);
        verify(locationValidationContract).validarLocationExistente(locationUuid);
        verify(repository).updateMinStockByImplementUuid(implementUuid, 3);
    }

    @Test
    void crearDebeFallarSiCategoriaInactivaONoExiste() {
        UUID categoryUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        doThrow(new BadRequestException("CATEGORY_INACTIVE_OR_NOT_FOUND", "categoria invalida"))
                .when(categoryValidationContract)
                .validarCategoriaActivaParaImplemento(categoryUuid);

        assertThrows(BadRequestException.class, () ->
                service.crear("Guantes", null, categoryUuid, locationUuid, "fungible", 5, null, null, null));
        verify(repository, never()).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void crearDebeFallarSiItemTypeNoEsValido() {
        UUID categoryUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.crear("Guantes", null, categoryUuid, locationUuid, "otro", 5, null, null, null));

        assertEquals("IMPLEMENT_ITEM_TYPE_INVALID", ex.getCode());
    }

    @Test
    void crearDebeFallarConBadRequestSiNombreActivoYaExiste() {
        UUID categoryUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        when(repository.existsActiveByNameIgnoreCase("Guantes", categoryUuid)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.crear("Guantes", null, categoryUuid, locationUuid, "fungible", 4, null, null, null));

        assertEquals("IMPLEMENT_NAME_DUPLICATE", ex.getCode());
    }

    @Test
    void crearDebeRetornarBadRequestSiNombreDuplicadoPorConstraintUnico() {
        UUID categoryUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        when(repository.create("Guantes", null, categoryUuid, locationUuid, ImplementItemType.FUNGIBLE, null, null, null))
                .thenThrow(new DataIntegrityViolationException("unique violation", new SQLException("duplicate key", "23505")));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.crear("Guantes", null, categoryUuid, locationUuid, "fungible", 3, null, null, null));

        assertEquals("IMPLEMENT_NAME_DUPLICATE", ex.getCode());
    }

    @Test
    void editarDebeFallarSiImplementoNoExiste() {
        UUID implementUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        when(repository.findByUuid(implementUuid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.editar(implementUuid, "X", null, null, locationUuid, "no_fungible", 1, null, null, null));
    }

    @Test
    void editarDebeValidarCategoriaSiExisteImplemento() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID implementUuid = UUID.randomUUID();
        UUID categoryUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        Implemento existing = new Implemento(implementUuid, "Existente", null, categoryUuid, locationUuid, ImplementItemType.NO_FUNGIBLE, null, null, null, true, now, now);
        Implemento updated = new Implemento(implementUuid, "Nuevo", null, categoryUuid, locationUuid, ImplementItemType.NO_FUNGIBLE, "Obs", null, null, true, now, now);

        when(repository.findByUuid(implementUuid)).thenReturn(Optional.of(existing));
        when(repository.update(implementUuid, "Nuevo", null, categoryUuid, locationUuid, ImplementItemType.NO_FUNGIBLE, "Obs", null, null)).thenReturn(updated);
        when(repository.updateMinStockByImplementUuid(implementUuid, 1)).thenReturn(1);

        Implemento result = service.editar(implementUuid, "Nuevo", null, categoryUuid, locationUuid, "no_fungible", 1, "Obs", null, null);

        assertEquals(categoryUuid, result.categoriaUuid());
        verify(categoryValidationContract).validarCategoriaActivaParaImplemento(categoryUuid);
        verify(locationValidationContract).validarLocationExistente(locationUuid);
        verify(repository).updateMinStockByImplementUuid(implementUuid, 1);
    }

    @Test
    void editarDebeFallarSiImplementoEstaInactivo() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID implementUuid = UUID.randomUUID();
        UUID categoryUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        Implemento existing = new Implemento(implementUuid, "Existente", null, categoryUuid, locationUuid, ImplementItemType.NO_FUNGIBLE, null, null, null, false, now, now);
        when(repository.findByUuid(implementUuid)).thenReturn(Optional.of(existing));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.editar(implementUuid, "Nuevo", null, categoryUuid, locationUuid, "no_fungible", 1, null, null, null));

        assertEquals("IMPLEMENT_INACTIVE", ex.getCode());
        verify(repository, never()).update(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void editarDebeFallarConBadRequestSiNombreActivoExisteEnOtroImplemento() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID implementUuid = UUID.randomUUID();
        UUID categoryUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        Implemento existing = new Implemento(implementUuid, "Existente", null, categoryUuid, locationUuid, ImplementItemType.NO_FUNGIBLE, null, null, null, true, now, now);
        when(repository.findByUuid(implementUuid)).thenReturn(Optional.of(existing));
        when(repository.existsActiveByNameIgnoreCaseAndUuidNot("Guantes", categoryUuid, implementUuid)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.editar(implementUuid, "Guantes", null, categoryUuid, locationUuid, "no_fungible", 1, null, null, null));

        assertEquals("IMPLEMENT_NAME_DUPLICATE", ex.getCode());
    }

    @Test
    void listarDebeAplicarFiltrosCombinados() {
        UUID categoryUuid = UUID.randomUUID();
        when(repository.findAllSummaries("Guante", categoryUuid, null)).thenReturn(java.util.List.of());

        service.listar("  Guante ", categoryUuid, null);

        verify(repository).findAllSummaries("Guante", categoryUuid, null);
    }

    @Test
    void listarConFiltroDamagedDebePropagarsAlRepositorio() {
        when(repository.findAllSummaries(null, null, StockStatusFilter.DAMAGED)).thenReturn(java.util.List.of());

        service.listar(null, null, StockStatusFilter.DAMAGED);

        verify(repository).findAllSummaries(null, null, StockStatusFilter.DAMAGED);
    }
}
