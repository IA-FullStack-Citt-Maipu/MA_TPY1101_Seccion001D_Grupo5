package com.panol_project.backendpanol.modules.catalog.stock.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementRepository;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockRepository;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryMovementServiceTest {

    @Mock
    private InventoryMovementRepository repository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private OutboxService outboxService;

    @Test
    void obtenerTodosMovimientosDebeConsultarRepositorio() {
        UUID implementUuid = UUID.randomUUID();
        UUID performerUuid = UUID.randomUUID();
        InventoryMovement movement = new InventoryMovement(
                implementUuid,
                MovementAction.STOCK_IN,
                3,
                performerUuid,
                Instant.now(),
                "Nota"
        );
        when(repository.findAllByOrderByTimestampDesc()).thenReturn(List.of(movement));

        InventoryMovementService service = new InventoryMovementService(repository, stockRepository, outboxService);
        List<InventoryMovement> resultado = service.obtenerTodosMovimientos();

        assertEquals(1, resultado.size());
        assertEquals("Nota", resultado.get(0).getNotes());
        verify(repository).findAllByOrderByTimestampDesc();
    }

    @Test
    void obtenerTodosMovimientosDebeSerReadOnly() throws Exception {
        Method method = InventoryMovementService.class.getMethod("obtenerTodosMovimientos");
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional, "El método obtenerTodosMovimientos debe estar anotado con @Transactional");
        assertTrue(transactional.readOnly(), "El método obtenerTodosMovimientos debe ejecutarse en readOnly=true");
    }
}
