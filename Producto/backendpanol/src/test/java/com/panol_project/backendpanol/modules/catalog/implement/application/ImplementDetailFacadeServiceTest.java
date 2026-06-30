package com.panol_project.backendpanol.modules.catalog.implement.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.stock.application.contract.InventoryMovementQueryContract;
import com.panol_project.backendpanol.modules.catalog.stock.application.contract.InventoryMovementQueryContract.InventoryMovementView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImplementDetailFacadeServiceTest {

    @Mock
    private InventoryMovementQueryContract inventoryMovementQueryContract;

    @Test
    void shouldDelegateToContracts() {
        UUID implementUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        InventoryMovementView movement = new InventoryMovementView(
                "m1",
                implementUuid,
                "STOCK_IN",
                3,
                userUuid,
                "Ana",
                Instant.now(),
                "ok"
        );

        ImplementDetailFacadeService service = new ImplementDetailFacadeService(inventoryMovementQueryContract);

        when(inventoryMovementQueryContract.obtenerUltimosMovimientosPorImplemento(implementUuid)).thenReturn(List.of(movement));

        List<ImplementRecentMovement> resultMovements = service.getRecentMovements(implementUuid);

        assertEquals(1, resultMovements.size());
        assertEquals("Ana", resultMovements.getFirst().performedByName());
    }
}
