package com.panol_project.backendpanol.modules.catalog.stock.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.stock.domain.IndividualEntryDraft;
import com.panol_project.backendpanol.modules.catalog.stock.domain.IndividualItem;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
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
import org.mockito.ArgumentCaptor;
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
                        StockItemType.INDIVIDUAL,
                        true
                )));
        when(repository.findStockByImplementUuid(implementUuid))
                .thenReturn(Optional.of(new StockCounters(3, 2, 1, 1, 0, 1)));
        when(repository.findActiveIndividualsByImplementUuid(implementUuid))
                .thenReturn(List.of(
                        new IndividualItem(UUID.randomUUID(), implementUuid, "A1", "available", "good", null, locationUuid, true, 38, false),
                        new IndividualItem(UUID.randomUUID(), implementUuid, "A2", "loaned", "damaged_repairable", null, locationUuid, true, null, false),
                        new IndividualItem(UUID.randomUUID(), implementUuid, "A3", "damaged", "damaged_no_diagnosis", null, locationUuid, true, -13, true)
                ));

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);
        StockDetail detail = service.getStockDetail(implementUuid);

        assertEquals(StockItemType.INDIVIDUAL, detail.itemType());
        assertEquals(3, detail.stock().totalStock());
        assertEquals(1, detail.stock().available());
        assertEquals(1, detail.stock().reserved());
        assertEquals(1, detail.stock().damaged());
        assertEquals(38, detail.individuals().getFirst().remainingLife());
        assertEquals(false, detail.individuals().getFirst().assetCodeReprintRequired());
    }

    @Test
    void addEntryDebeTraducirErrorDelTriggerCuandoSeIntentaSerializarFungible() {
        UUID implementUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();

        when(repository.findImplementContext(implementUuid))
                .thenReturn(Optional.of(new StockRepository.ImplementStockContext(
                        implementUuid,
                        locationUuid,
                        StockItemType.CONSUMABLE,
                        true
                )));

        doThrow(newJooqDataAccessException(
                "ERROR: raised by trigger trg_guard_individual_item_type (fn_guard_individual_item_type)"
        )).when(repository).createIndividuals(eq(implementUuid), any());

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.addEntry(implementUuid, 1, List.of("SER-001"), null, null, null, null, null, null, List.of())
        );

        assertEquals("INDIVIDUAL_NOT_ALLOWED_FOR_ITEM_TYPE", ex.getCode());
        verify(repository, never()).updateStock(eq(implementUuid), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void updateIndividualDebePropagarRemainingLifeYReprintFlag() {
        UUID implementUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        UUID individualUuid = UUID.randomUUID();

        when(repository.findImplementContext(implementUuid))
                .thenReturn(Optional.of(new StockRepository.ImplementStockContext(
                        implementUuid,
                        locationUuid,
                        StockItemType.INDIVIDUAL,
                        true
                )));
        when(repository.findActiveIndividualsByUuids(implementUuid, List.of(individualUuid)))
                .thenReturn(List.of(new IndividualItem(
                        individualUuid,
                        implementUuid,
                        "ACT-001",
                        "available",
                        "good",
                        null,
                        locationUuid,
                        true,
                        null,
                        false
                )));
        when(repository.findStockByImplementUuid(implementUuid))
                .thenReturn(Optional.of(new StockCounters(1, 0, 1, 0, 0, 0)));
        when(repository.findActiveIndividualsByImplementUuid(implementUuid))
                .thenReturn(List.of(new IndividualItem(
                        individualUuid,
                        implementUuid,
                        "ACT-001",
                        "available",
                        "good",
                        "Actualizado",
                        locationUuid,
                        true,
                        38,
                        true
                )));
        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(UUID.randomUUID()));

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);
        StockDetail detail = service.updateIndividual(
                implementUuid,
                individualUuid,
                "available",
                "good",
                "Actualizado",
                locationUuid,
                true,
                38,
                true,
                true
        );

        verify(repository).updateIndividualsState(
                List.of(individualUuid),
                "available",
                "good",
                "Actualizado",
                locationUuid,
                true,
                38,
                true,
                true
        );
        assertEquals(38, detail.individuals().getFirst().remainingLife());
        assertEquals(true, detail.individuals().getFirst().assetCodeReprintRequired());
    }

    @Test
    void addEntryDebeCrearLoteConEstadoCondicionYNotaEnUnaSolaOperacion() {
        UUID implementUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        when(repository.findImplementContext(implementUuid))
                .thenReturn(Optional.of(new StockRepository.ImplementStockContext(
                        implementUuid,
                        locationUuid,
                        StockItemType.INDIVIDUAL,
                        true
                )));
        when(repository.findStockByImplementUuid(implementUuid))
                .thenReturn(Optional.of(new StockCounters(2, 0, 2, 0, 0, 0)));
        when(repository.findActiveIndividualsByImplementUuid(implementUuid))
                .thenReturn(List.of(
                        new IndividualItem(UUID.randomUUID(), implementUuid, "ACT-001", "maintenance", "damaged_repairable", null, locationUuid, true, null, false),
                        new IndividualItem(UUID.randomUUID(), implementUuid, "ACT-002", "maintenance", "damaged_repairable", null, locationUuid, true, null, false)
                ));
        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);
        StockDetail detail = service.addEntry(
                implementUuid,
                2,
                List.of("ACT-001", "ACT-002"),
                "maintenance",
                "damaged_repairable",
                "Ingreso sala 325",
                null,
                null,
                null,
                List.of()
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IndividualEntryDraft>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).createIndividuals(eq(implementUuid), entriesCaptor.capture());
        List<IndividualEntryDraft> entries = entriesCaptor.getValue();
        assertEquals(2, entries.size());
        assertEquals("ACT-001", entries.getFirst().assetCode());
        assertEquals("maintenance", entries.getFirst().status());
        assertEquals("damaged_repairable", entries.getFirst().condition());
        assertEquals(locationUuid, entries.getFirst().currentLocationUuid());
        verify(repository).replaceStock(implementUuid, 2, 0, 0, 0, 2);
        ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(movementCaptor.capture());
        InventoryMovement savedMovement = movementCaptor.getValue();
        assertNotNull(savedMovement);
        assertEquals("Ingreso sala 325", savedMovement.getNotes());
        assertEquals("maintenance", detail.individuals().getFirst().status());
        assertEquals("damaged_repairable", detail.individuals().getFirst().condition());
    }

    @Test
    void addEntryDebePropagarErroresDeBaseNoRelacionadosAlTrigger() {
        UUID implementUuid = UUID.randomUUID();
        UUID locationUuid = UUID.randomUUID();

        when(repository.findImplementContext(implementUuid))
                .thenReturn(Optional.of(new StockRepository.ImplementStockContext(
                        implementUuid,
                        locationUuid,
                        StockItemType.CONSUMABLE,
                        true
                )));

        doThrow(newJooqDataAccessException("timeout while writing to database"))
                .when(repository).createIndividuals(eq(implementUuid), any());

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.addEntry(implementUuid, 1, List.of("SER-002"), null, null, null, null, null, null, List.of())
        );
        assertEquals("timeout while writing to database", thrown.getMessage());

        verify(repository, never()).updateStock(eq(implementUuid), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void addEntryDebeAceptarCamposDistintosPorActivoCuandoSeEnvianIndividualEntries() {
        UUID implementUuid = UUID.randomUUID();
        UUID defaultLocationUuid = UUID.randomUUID();
        UUID customLocationUuid = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        when(repository.findImplementContext(implementUuid))
                .thenReturn(Optional.of(new StockRepository.ImplementStockContext(
                        implementUuid,
                        defaultLocationUuid,
                        StockItemType.INDIVIDUAL,
                        true
                )));
        when(repository.findStockByImplementUuid(implementUuid))
                .thenReturn(Optional.of(new StockCounters(2, 0, 1, 0, 1, 0)));
        when(repository.findActiveIndividualsByImplementUuid(implementUuid))
                .thenReturn(List.of(
                        new IndividualItem(UUID.randomUUID(), implementUuid, "ACT-010", "available", "good", null, customLocationUuid, true, 48, true),
                        new IndividualItem(UUID.randomUUID(), implementUuid, "ACT-011", "loaned", "damaged_repairable", null, defaultLocationUuid, true, 12, false)
                ));
        when(currentUserUuidResolver.resolveCurrentUserUuid()).thenReturn(Optional.of(actorUuid));

        StockService service = new StockService(repository, outboxService, inventoryMovementRepository, currentUserUuidResolver);
        service.addEntry(
                implementUuid,
                2,
                null,
                null,
                null,
                "Ingreso diferenciado",
                null,
                null,
                null,
                List.of(
                        new IndividualEntryDraft("ACT-010", "available", "good", customLocationUuid, 48, true),
                        new IndividualEntryDraft("ACT-011", "loaned", "damaged_repairable", null, 12, false)
                )
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IndividualEntryDraft>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).createIndividuals(eq(implementUuid), entriesCaptor.capture());
        List<IndividualEntryDraft> entries = entriesCaptor.getValue();
        assertEquals(customLocationUuid, entries.getFirst().currentLocationUuid());
        assertEquals(48, entries.getFirst().remainingLife());
        assertEquals(true, entries.getFirst().assetCodeReprintRequired());
        assertEquals(defaultLocationUuid, entries.get(1).currentLocationUuid());
        assertEquals(12, entries.get(1).remainingLife());
        assertEquals(false, entries.get(1).assetCodeReprintRequired());
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
