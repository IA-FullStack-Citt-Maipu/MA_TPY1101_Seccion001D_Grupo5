package com.panol_project.backendpanol.modules.catalog.stock.application;

import com.panol_project.backendpanol.modules.catalog.stock.application.contract.InventoryMovementQueryContract;
import com.panol_project.backendpanol.modules.catalog.stock.application.contract.InventoryMovementQueryContract.InventoryMovementView;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementRepository;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockRepository;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryMovementService implements InventoryMovementQueryContract {

    private final InventoryMovementRepository repository;
    private final StockRepository stockRepository;
    private final OutboxService outboxService;

    public InventoryMovementService(InventoryMovementRepository repository, StockRepository stockRepository, OutboxService outboxService) {
        this.repository = repository;
        this.stockRepository = stockRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public InventoryMovement registrarMovimiento(UUID implementUuid, MovementAction action, Integer quantity, UUID performedByUuid, String notes) {
        stockRepository.findImplementContext(implementUuid)
                .orElseThrow(() -> new NotFoundException("IMPLEMENT_NOT_FOUND", "Implemento no encontrado"));
        InventoryMovement movement = new InventoryMovement(
                implementUuid,
                action, 
                quantity, 
                performedByUuid,
                Instant.now(), 
                notes
        );
        InventoryMovement saved = repository.save(movement);
        outboxService.enqueue("implement", implementUuid, "InventoryMovementRegistered", performedByUuid, Map.of(
                "action", action.literal(),
                "quantity", quantity,
                "notes", notes == null ? "" : notes
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> obtenerUltimosMovimientos(UUID implementUuid) {
        return repository.findTop10ByImplementUuidOrderByTimestampDesc(implementUuid);
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> obtenerTodosMovimientos() {
        return repository.findAllByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> obtenerMovimientosRecientes(int limit) {
        if (limit <= 0) {
            return repository.findAllByOrderByTimestampDesc();
        }
        return repository.findByOrderByTimestampDesc(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMovementView> obtenerUltimosMovimientosPorImplemento(UUID implementUuid) {
        return repository.findTop10ByImplementUuidOrderByTimestampDesc(implementUuid)
                .stream()
                .map(this::toMovementView)
                .toList();
    }

    private InventoryMovementView toMovementView(InventoryMovement movement) {
        return new InventoryMovementView(
                movement.getId(),
                movement.getImplementUuid(),
                movement.getAction() == null ? null : movement.getAction().literal(),
                movement.getQuantity(),
                movement.getPerformedByUuid(),
                movement.getPerformedByName(),
                movement.getTimestamp(),
                movement.getNotes()
        );
    }
}
