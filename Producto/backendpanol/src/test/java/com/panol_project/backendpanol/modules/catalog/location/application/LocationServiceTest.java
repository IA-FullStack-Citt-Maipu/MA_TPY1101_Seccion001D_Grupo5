package com.panol_project.backendpanol.modules.catalog.location.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.location.domain.LocationOption;
import com.panol_project.backendpanol.modules.catalog.location.domain.LocationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository repository;

    @Test
    void listarSelectorDebeRetornarSoloResultadosDelRepositorio() {
        UUID uuid = UUID.randomUUID();
        when(repository.findAllActive()).thenReturn(List.of(new LocationOption(uuid, "Box A", "Estante norte", true)));

        LocationService service = new LocationService(repository);
        List<LocationOption> result = service.listarSelector();

        assertEquals(1, result.size());
        assertEquals(uuid, result.getFirst().uuid());
        verify(repository).findAllActive();
    }

    @Test
    void listarGestionDebeRetornarActivasEInactivasDesdeRepositorio() {
        UUID activeUuid = UUID.randomUUID();
        UUID inactiveUuid = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
                new LocationOption(activeUuid, "Lab 1", null, true),
                new LocationOption(inactiveUuid, "Lab 2", null, false)
        ));

        LocationService service = new LocationService(repository);
        List<LocationOption> result = service.listarGestion();

        assertEquals(2, result.size());
        verify(repository).findAll();
    }

    @Test
    void eliminarDebeAplicarSoftDeleteCuandoLaUbicacionEstaActiva() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(new LocationOption(uuid, "Lab 1", null, true)));

        LocationService service = new LocationService(repository);
        service.eliminar(uuid);

        verify(repository).softDelete(uuid);
    }

    @Test
    void eliminarNoDebeHacerNadaSiLaUbicacionYaEstaInactiva() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(new LocationOption(uuid, "Lab 2", null, false)));

        LocationService service = new LocationService(repository);
        service.eliminar(uuid);

        verify(repository, never()).softDelete(uuid);
    }
}
