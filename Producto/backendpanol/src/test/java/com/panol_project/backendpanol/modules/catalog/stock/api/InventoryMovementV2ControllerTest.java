package com.panol_project.backendpanol.modules.catalog.stock.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.stock.api.dto.InventoryMovementV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.application.InventoryMovementService;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryMovementV2ControllerTest {

    @Mock
    private InventoryMovementService inventoryMovementService;

    @Mock
    private CurrentUserUuidResolver currentUserUuidResolver;

    @Test
    void listarMovimientosDebeResolverNombreDesdeContrato() {
        UUID implementUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        InventoryMovement movement = new InventoryMovement(
                implementUuid,
                MovementAction.STOCK_IN,
                2,
                userUuid,
                Instant.now(),
                "nota"
        );
        movement.setId("m1");

        InventoryMovementV2Controller controller = new InventoryMovementV2Controller(
                inventoryMovementService,
                currentUserUuidResolver
        );

        when(inventoryMovementService.obtenerTodosMovimientos()).thenReturn(List.of(movement));
        movement.setPerformedByName("Carlos");

        List<InventoryMovementV2Response> response = controller.listarMovimientos();

        assertEquals(1, response.size());
        assertEquals("Carlos", response.get(0).performedBy());
        verify(inventoryMovementService).obtenerTodosMovimientos();
    }

    @Test
    void listarMovimientosDebeResolverUsuarioNoIdentificadoSiNoExisteEnDirectorio() {
        UUID implementUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        InventoryMovement movement = new InventoryMovement(
                implementUuid,
                MovementAction.STOCK_OUT,
                1,
                userUuid,
                Instant.now(),
                null
        );

        InventoryMovementV2Controller controller = new InventoryMovementV2Controller(
                inventoryMovementService,
                currentUserUuidResolver
        );

        when(inventoryMovementService.obtenerTodosMovimientos()).thenReturn(List.of(movement));

        List<InventoryMovementV2Response> response = controller.listarMovimientos();

        assertEquals(1, response.size());
        assertEquals("Usuario no identificado", response.get(0).performedBy());
    }
}
