package com.panol_project.backendpanol.modules.catalog.stock.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.stock.domain.IndividualItem;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementRepository;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockCounters;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockDetail;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockItemType;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockRepository;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository repository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private CurrentUserUuidResolver currentUserUuidResolver;

    @Test
    void getStockDetailDebeUsarStockItemTypeLocalYRetornarStockPersistido() {
        UUID implementUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        when(repository.findImplementContext(implementUuid))
                .thenReturn(Optional.of(new StockRepository.ImplementStockContext(
                        implementUuid,
                        locationUuid,
                        StockItemType.NO_FUNGIBLE,
                        true
                )));
        when(repository.findStockByImplementUuid(implementUuid))
                .thenReturn(Optional.of(new StockCounters(3, 2, 1, 1, 0, 1)));
        when(repository.findActiveIndividualsByImplementUuid(implementUuid))
                .thenReturn(List.of(
                        new IndividualItem(UUID.randomUUID(), implementUuid, "A1", "available", "good", null, locationUuid, true),
                        new IndividualItem(UUID.randomUUID(), implementUuid, "A2", "loaned", "fair", null, locationUuid, true),
                        new IndividualItem(UUID.randomUUID(), implementUuid, "A3", "damaged", "poor", null, locationUuid, true)
                ));

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);
        StockDetail detail = service.getStockDetail(implementUuid);

        assertEquals(StockItemType.NO_FUNGIBLE, detail.itemType());
        assertEquals(3, detail.stock().totalStock());
        assertEquals(1, detail.stock().available());
        assertEquals(1, detail.stock().reserved());
        assertEquals(1, detail.stock().damaged());
    }

    @Test
    void addEntryDebeTraducirErrorDelTriggerCuandoSeIntentaSerializarFungible() {
        UUID implementUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();

        when(repository.findImplementContext(implementUuid))
                .thenReturn(Optional.of(new StockRepository.ImplementStockContext(
                        implementUuid,
                        locationUuid,
                        StockItemType.FUNGIBLE,
                        true
                )));

        doThrow(newJooqDataAccessException(
                "ERROR: raised by trigger trg_guard_individual_no_fungible (individual_no_fungible)"
        )).when(repository).createIndividuals(eq(implementUuid), eq(locationUuid), any());

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.addEntry(implementUuid, 1, List.of("SER-001"))
        );

        assertEquals("INDIVIDUAL_NOT_ALLOWED_FOR_FUNGIBLE", ex.getCode());
        verify(repository, never()).updateStock(eq(implementUuid), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void addEntryDebePropagarErroresDeBaseNoRelacionadosAlTrigger() {
        UUID implementUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();

        when(repository.findImplementContext(implementUuid))
                .thenReturn(Optional.of(new StockRepository.ImplementStockContext(
                        implementUuid,
                        locationUuid,
                        StockItemType.FUNGIBLE,
                        true
                )));

        doThrow(newJooqDataAccessException("timeout while writing to database"))
                .when(repository).createIndividuals(eq(implementUuid), eq(locationUuid), any());

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.addEntry(implementUuid, 1, List.of("SER-002"))
        );
        assertEquals("timeout while writing to database", thrown.getMessage());

        verify(repository, never()).updateStock(eq(implementUuid), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    private RuntimeException newJooqDataAccessException(String message) {
        try {
            Class<?> type = Class.forName("org.jooq.exception.DataAccessException");
            Constructor<?> constructor = type.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(message);
            if (instance instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
            throw new IllegalStateException("org.jooq.exception.DataAccessException no es RuntimeException");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("No se pudo crear la excepción de jOOQ para la prueba", ex);
        }
    }
}
