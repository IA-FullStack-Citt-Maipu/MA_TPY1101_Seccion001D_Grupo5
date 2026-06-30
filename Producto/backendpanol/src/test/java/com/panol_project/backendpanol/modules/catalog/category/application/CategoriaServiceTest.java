package com.panol_project.backendpanol.modules.catalog.category.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.category.domain.Categoria;
import com.panol_project.backendpanol.modules.catalog.category.domain.CategoriaRepository;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.ConflictException;
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
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository repository;

    private CategoriaService service;

    @BeforeEach
    void setUp() {
        service = new CategoriaService(repository);
    }

    @Test
    void crearDebeFallarConBadRequestSiNombreDuplicado() {
        when(repository.existsByNombre("Reactivos", null)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.crear("Reactivos", null));

        assertEquals("CATEGORY_NAME_DUPLICATE", ex.getCode());
    }

    @Test
    void desactivarDebeRetornarConflictSiHayImplementsActivosSinForce() {
        UUID uuid = UUID.randomUUID();
        Categoria record = new Categoria(uuid, "Reactivos", null, true, OffsetDateTime.now());

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(record));
        when(repository.countActiveImplementsByCategoryUuid(uuid)).thenReturn(3);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.desactivar(uuid, false));

        assertEquals("CATEGORY_HAS_ACTIVE_IMPLEMENTS", ex.getCode());
        verify(repository, never()).deactivate(any());
    }

    @Test
    void eliminarDebeFallarSiTieneImplementsAsociados() {
        UUID uuid = UUID.randomUUID();
        Categoria record = new Categoria(uuid, "Insumos", null, true, OffsetDateTime.now());

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(record));
        when(repository.countImplementsByCategoryUuid(uuid)).thenReturn(2);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.eliminar(uuid));

        assertEquals("CATEGORY_HAS_IMPLEMENTS", ex.getCode());
        verify(repository, never()).deleteByUuid(any());
    }

    @Test
    void validarCategoriaActivaParaImplementoDebeFallarSiInactiva() {
        UUID uuid = UUID.randomUUID();
        when(repository.findActiveByUuid(uuid)).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validarCategoriaActivaParaImplemento(uuid));

        assertEquals("CATEGORY_INACTIVE_OR_NOT_FOUND", ex.getCode());
    }

    @Test
    void desactivarConForceDebeDesactivarYRetornarCategoria() {
        UUID uuid = UUID.randomUUID();
        Categoria active = new Categoria(uuid, "Activa", null, true, OffsetDateTime.now());

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(active));
        when(repository.countActiveImplementsByCategoryUuid(uuid)).thenReturn(1);

        Categoria response = service.desactivar(uuid, true);

        assertEquals(uuid, response.uuid());
        assertEquals(false, response.activa());
        verify(repository).deactivate(eq(uuid));
    }

    @Test
    void activarDebeRetornarCategoriaCuandoYaEstaActiva() {
        UUID uuid = UUID.randomUUID();
        Categoria active = new Categoria(uuid, "Quimica", null, true, OffsetDateTime.now());

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(active));

        Categoria response = service.activar(uuid);

        assertEquals("Quimica", response.nombre());
        assertEquals(true, response.activa());
        verify(repository, never()).activate(any());
    }

    @Test
    void activarDebeActivarCuandoEstaInactiva() {
        UUID uuid = UUID.randomUUID();
        Categoria inactive = new Categoria(uuid, "Biologia", null, false, OffsetDateTime.now());

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(inactive));

        Categoria response = service.activar(uuid);

        assertEquals(false, inactive.activa());
        assertEquals(true, response.activa());
        verify(repository).activate(eq(uuid));
    }

    @Test
    void activarDebeLanzarBadRequestCuandoActivaNombreDuplicadoViolandoRestriccion() {
        UUID uuid = UUID.randomUUID();
        Categoria inactive = new Categoria(uuid, "Duplicada", null, false, OffsetDateTime.now());

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(inactive));
        doThrow(new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint \"category_name_key\""))
                .when(repository).activate(eq(uuid));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.activar(uuid));

        assertEquals("CATEGORY_NAME_ACTIVE_DUPLICATE", ex.getCode());
        assertEquals("Ya existe una categoría activa con ese nombre.", ex.getMessage());
    }
}

