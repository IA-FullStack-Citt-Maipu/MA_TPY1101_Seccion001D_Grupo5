package com.panol_project.backendpanol.modules.catalog.stock.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementDashboardSummary;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryFilter;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementHistoryPage;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementRepository;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementTopImplementStat;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementTopUserStat;
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

    @Test
    void obtenerHistorialDebeConsultarRepositorioPaginado() {
        InventoryMovementHistoryFilter filter = new InventoryMovementHistoryFilter("guantes", MovementAction.STOCK_IN, null, null);
        InventoryMovementHistoryPage expected = new InventoryMovementHistoryPage(List.of(), 1, 15, 0, 1);
        when(repository.findHistory(filter, 1, 15)).thenReturn(expected);

        InventoryMovementService service = new InventoryMovementService(repository, stockRepository, outboxService);
        InventoryMovementHistoryPage resultado = service.obtenerHistorial(filter, 1, 15);

        assertEquals(1, resultado.page());
        assertEquals(15, resultado.size());
        verify(repository).findHistory(filter, 1, 15);
    }

    @Test
    void obtenerResumenDashboardDebeConsultarRepositorio() {
        InventoryMovementDashboardSummary expected = new InventoryMovementDashboardSummary(
                12,
                List.of(new InventoryMovementTopUserStat("Ana", "COORDINADOR", 7)),
                List.of(new InventoryMovementTopImplementStat(UUID.randomUUID(), "Simulador", 5))
        );
        when(repository.findDashboardSummary()).thenReturn(expected);

        InventoryMovementService service = new InventoryMovementService(repository, stockRepository, outboxService);
        InventoryMovementDashboardSummary resultado = service.obtenerResumenDashboard();

        assertEquals(12, resultado.totalMovements());
        assertEquals(1, resultado.topUsers().size());
        verify(repository).findDashboardSummary();
    }
}
