package com.panol_project.backendpanol.modules.catalog.stock.application;

import com.panol_project.backendpanol.modules.catalog.stock.application.contract.StockMovementContract;
import com.panol_project.backendpanol.modules.catalog.stock.domain.IndividualItem;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovement;
import com.panol_project.backendpanol.modules.catalog.stock.domain.InventoryMovementRepository;
import com.panol_project.backendpanol.modules.catalog.stock.domain.MovementAction;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockCounters;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockDetail;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockItemType;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockMovementType;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockRepository;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.ConflictException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jooq.exception.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService implements StockMovementContract {

    private static final Set<String> VALID_INDIVIDUAL_STATUS = Set.of(
            "available", "loaned", "maintenance", "damaged"
    );

    private static final Set<String> VALID_INDIVIDUAL_CONDITION = Set.of(
            "good", "fair", "poor"
    );

    private final StockRepository repository;
    private final OutboxService outboxService;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final CurrentUserUuidResolver currentUserUuidResolver;

    public StockService(
            StockRepository repository,
            OutboxService outboxService,
            InventoryMovementRepository inventoryMovementRepository,
            CurrentUserUuidResolver currentUserUuidResolver
    ) {
        this.repository = repository;
        this.outboxService = outboxService;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.currentUserUuidResolver = currentUserUuidResolver;
    }

    @Transactional(readOnly = true)
    public StockDetail getStockDetail(UUID implementUuid) {
        var context = requireContext(implementUuid);

        StockCounters counters = repository.findStockByImplementUuid(implementUuid)
                .orElse(new StockCounters(0, 0, 0, 0, 0, 0));

        List<IndividualItem> individuals = context.itemType() == StockItemType.NO_FUNGIBLE
                ? repository.findActiveIndividualsByImplementUuid(implementUuid)
                : List.of();

        return new StockDetail(implementUuid, context.itemType(), counters, individuals);
    }

    @Transactional
    public StockDetail addEntry(UUID implementUuid, Integer quantity, List<String> assetCodes) {
        var context = requireContext(implementUuid);
        int qty = requirePositiveQuantity(quantity);

        repository.ensureStockRow(implementUuid);

        boolean shouldCreateIndividuals = context.itemType() == StockItemType.NO_FUNGIBLE
                || (assetCodes != null && !assetCodes.isEmpty());

        if (shouldCreateIndividuals) {
            List<String> normalizedCodes = normalizeAssetCodes(assetCodes, qty);
            try {
                repository.createIndividuals(implementUuid, context.locationUuid(), normalizedCodes);
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("INDIVIDUAL_ASSET_CODE_DUPLICATE", "Uno o mas codigos de activo ya existen");
            } catch (DataAccessException ex) {
                if (isNoFungibleGuardViolation(ex)) {
                    throw new BadRequestException(
                            "INDIVIDUAL_NOT_ALLOWED_FOR_FUNGIBLE",
                            "No se pueden crear unidades individuales para implementos fungibles"
                    );
                }
                throw ex;
            }
        }

        repository.updateStock(implementUuid, qty, qty, 0, 0, 0);
        recordInventoryMovement(implementUuid, MovementAction.STOCK_IN, qty, "Stock entry");
        outboxService.enqueue("implement", implementUuid, "StockEntryAdded", null, java.util.Map.of("quantity", qty));

        return getStockDetail(implementUuid);
    }

    @Transactional
    public StockDetail applyMovement(
            UUID implementUuid,
            StockMovementType movementType,
            Integer quantity,
            List<UUID> individualUuids,
            String conditionRaw
    ) {
        var context = requireContext(implementUuid);
        if (movementType == null) {
            throw new BadRequestException(
                    "STOCK_MOVEMENT_TYPE_INVALID",
                    "movement_type invalido. Usa STOCK_IN, STOCK_OUT, LOAN_DELIVERY, LOAN_RETURN, DAMAGE_REPORT o MANUAL_ADJUSTMENT"
            );
        }

        repository.ensureStockRow(implementUuid);

        int movementQty;
        if (context.itemType() == StockItemType.NO_FUNGIBLE) {
            movementQty = applyMovementForIndividualImplement(context, movementType, individualUuids, conditionRaw);
        } else {
            movementQty = applyMovementForFungibleImplement(context, movementType, quantity);
        }

        recordInventoryMovement(implementUuid, MovementAction.valueOf(movementType.name()), movementQty, "Stock movement");
        outboxService.enqueue("implement", implementUuid, "StockMovementApplied", null, java.util.Map.of("movement_type", movementType.name()));
        return getStockDetail(implementUuid);
    }

    @Override
    @Transactional
    public void applyMovement(
            UUID implementUuid,
            String movementType,
            Integer quantity,
            List<UUID> individualUuids,
            String condition
    ) {
        StockMovementType parsedType = StockMovementType.fromLiteral(movementType)
                .orElseThrow(() -> new BadRequestException(
                        "STOCK_MOVEMENT_TYPE_INVALID",
                        "movement_type invalido. Usa STOCK_IN, STOCK_OUT, LOAN_DELIVERY, LOAN_RETURN, DAMAGE_REPORT o MANUAL_ADJUSTMENT"
                ));

        applyMovement(implementUuid, parsedType, quantity, individualUuids, condition);
    }

    @Transactional
    public StockDetail updateIndividual(
            UUID implementUuid,
            UUID individualUuid,
            String statusRaw,
            String conditionRaw,
            String notesRaw,
            UUID locationUuid,
            Boolean active
    ) {
        var context = requireContext(implementUuid);
        if (context.itemType() != StockItemType.NO_FUNGIBLE) {
            throw new BadRequestException("INDIVIDUAL_NOT_ALLOWED", "Solo los implementos no_fungible tienen registros individuales");
        }

        String status = normalizeOptionalLiteral(statusRaw, VALID_INDIVIDUAL_STATUS, "INDIVIDUAL_STATUS_INVALID", "status invalido");
        String condition = normalizeOptionalLiteral(conditionRaw, VALID_INDIVIDUAL_CONDITION, "INDIVIDUAL_CONDITION_INVALID", "condition invalido");
        String notes = normalizeOptionalText(notesRaw);

        List<IndividualItem> items = repository.findActiveIndividualsByUuids(implementUuid, List.of(individualUuid));
        if (items.isEmpty()) {
            throw new NotFoundException("INDIVIDUAL_NOT_FOUND", "Individual no encontrado para el implemento");
        }

        repository.updateIndividualsState(List.of(individualUuid), status, condition, notes, locationUuid, active);
        syncStockRowForIndividuals(implementUuid);
        recordInventoryMovement(implementUuid, MovementAction.MANUAL_ADJUSTMENT, 1, "Individual updated");
        outboxService.enqueue("implement", implementUuid, "StockIndividualUpdated", null, java.util.Map.of("individual_uuid", individualUuid.toString()));
        return getStockDetail(implementUuid);
    }

    private int applyMovementForFungibleImplement(
            StockRepository.ImplementStockContext context,
            StockMovementType movementType,
            Integer quantity
    ) {
        int qty;
        if (movementType == StockMovementType.MANUAL_ADJUSTMENT) {
            qty = requireNonZeroQuantity(quantity);
        } else {
            qty = requirePositiveQuantity(quantity);
        }

        applyMovementDelta(context.implementUuid(), movementType, qty);
        return qty;
    }

    private int applyMovementForIndividualImplement(
            StockRepository.ImplementStockContext context,
            StockMovementType movementType,
            List<UUID> individualUuids,
            String conditionRaw
    ) {
        List<UUID> uuids = normalizeIndividualUuids(individualUuids);
        List<IndividualItem> selected = repository.findActiveIndividualsByUuids(context.implementUuid(), uuids);
        if (selected.size() != uuids.size()) {
            throw new BadRequestException("INDIVIDUAL_SELECTION_INVALID", "Algunos individuales no pertenecen al implemento o no estan activos");
        }

        int qty = selected.size();
        String condition = normalizeOptionalLiteral(conditionRaw, VALID_INDIVIDUAL_CONDITION, "INDIVIDUAL_CONDITION_INVALID", "condition invalido");

        switch (movementType) {
            case STOCK_IN -> throw new BadRequestException("INDIVIDUAL_MOVEMENT_INVALID", "Para sumar stock individual usa /entries con asset_codes");
            case STOCK_OUT -> {
                applyMovementDelta(context.implementUuid(), movementType, qty);
                repository.updateIndividualsState(uuids, "damaged", condition == null ? "poor" : condition, null, null, false);
            }
            case LOAN_DELIVERY -> {
                applyMovementDelta(context.implementUuid(), movementType, qty);
                repository.updateIndividualsState(uuids, "loaned", condition, null, null, null);
            }
            case LOAN_RETURN -> {
                applyMovementDelta(context.implementUuid(), movementType, qty);
                repository.updateIndividualsState(uuids, "available", condition == null ? "good" : condition, null, context.locationUuid(), null);
            }
            case DAMAGE_REPORT -> {
                applyMovementDelta(context.implementUuid(), movementType, qty);
                repository.updateIndividualsState(uuids, "damaged", condition == null ? "poor" : condition, null, null, null);
            }
            case MANUAL_ADJUSTMENT ->
                    repository.updateIndividualsState(uuids, "available", condition == null ? "fair" : condition, null, null, null);
        }

        syncStockRowForIndividuals(context.implementUuid());
        return qty;
    }

    private void applyMovementDelta(UUID implementUuid, StockMovementType movementType, int qty) {
        StockCounters current = repository.findStockByImplementUuid(implementUuid)
                .orElse(new StockCounters(0, 0, 0, 0, 0, 0));

        int total = safe(current.totalStock());
        int available = safe(current.available());
        int reserved = safe(current.reserved());
        int loaned = safe(current.loaned());
        int damaged = safe(current.damaged());

        int totalDelta = 0;
        int availableDelta = 0;
        int reservedDelta = 0;
        int loanedDelta = 0;
        int damagedDelta = 0;

        switch (movementType) {
            case STOCK_IN -> {
                totalDelta = qty;
                availableDelta = qty;
            }
            case STOCK_OUT -> {
                totalDelta = -qty;
                availableDelta = -qty;
            }
            case LOAN_DELIVERY -> {
                availableDelta = -qty;
                loanedDelta = qty;
            }
            case LOAN_RETURN -> {
                availableDelta = qty;
                loanedDelta = -qty;
            }
            case DAMAGE_REPORT -> {
                availableDelta = -qty;
                damagedDelta = qty;
            }
            case MANUAL_ADJUSTMENT -> {
                totalDelta = qty;
                availableDelta = qty;
            }
        }

        int nextTotal = total + totalDelta;
        int nextAvailable = available + availableDelta;
        int nextReserved = reserved + reservedDelta;
        int nextLoaned = loaned + loanedDelta;
        int nextDamaged = damaged + damagedDelta;

        if (nextTotal < 0 || nextAvailable < 0 || nextReserved < 0 || nextLoaned < 0 || nextDamaged < 0) {
            throw new BadRequestException("STOCK_INSUFFICIENT", "El movimiento genera stock negativo");
        }

        if ((nextAvailable + nextReserved + nextLoaned + nextDamaged) > nextTotal) {
            throw new BadRequestException("STOCK_INVARIANT_BROKEN", "El movimiento rompe la invariante de stock");
        }

        repository.updateStock(implementUuid, totalDelta, availableDelta, reservedDelta, loanedDelta, damagedDelta);
    }

    private StockCounters deriveCountersForIndividuals(StockCounters current, List<IndividualItem> individuals) {
        int total = individuals.size();
        int available = 0;
        int reserved = safe(current.reserved());
        int loaned = 0;
        int damaged = 0;

        for (IndividualItem individual : individuals) {
            String status = individual.status() == null ? "" : individual.status();
            switch (status) {
                case "available" -> available++;
                case "loaned" -> loaned++;
                case "damaged", "maintenance" -> damaged++;
                default -> {
                }
            }
        }

        return new StockCounters(total, current.minStock(), available, reserved, loaned, damaged);
    }

    private void syncStockRowForIndividuals(UUID implementUuid) {
        StockCounters current = repository.findStockByImplementUuid(implementUuid)
                .orElse(new StockCounters(0, 0, 0, 0, 0, 0));
        List<IndividualItem> individuals = repository.findActiveIndividualsByImplementUuid(implementUuid);
        StockCounters computed = deriveCountersForIndividuals(current, individuals);

        boolean isDifferent = safe(current.totalStock()) != safe(computed.totalStock())
                || safe(current.available()) != safe(computed.available())
                || safe(current.reserved()) != safe(computed.reserved())
                || safe(current.loaned()) != safe(computed.loaned())
                || safe(current.damaged()) != safe(computed.damaged());

        if (isDifferent) {
            repository.replaceStock(
                    implementUuid,
                    safe(computed.totalStock()),
                    safe(computed.available()),
                    safe(computed.reserved()),
                    safe(computed.loaned()),
                    safe(computed.damaged())
            );
        }
    }

    private StockRepository.ImplementStockContext requireContext(UUID implementUuid) {
        var context = repository.findImplementContext(implementUuid)
                .orElseThrow(() -> new NotFoundException("IMPLEMENT_NOT_FOUND", "Implemento no encontrado"));

        if (!Boolean.TRUE.equals(context.active())) {
            throw new BadRequestException("IMPLEMENT_INACTIVE", "No se pueden gestionar stocks de un producto inactivo");
        }

        if (context.itemType() == null) {
            throw new BadRequestException("IMPLEMENT_ITEM_TYPE_MISSING", "El implemento no tiene item_type configurado");
        }

        return context;
    }

    private int requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("STOCK_QUANTITY_INVALID", "quantity debe ser un entero positivo");
        }
        return quantity;
    }

    private int requireNonZeroQuantity(Integer quantity) {
        if (quantity == null || quantity == 0) {
            throw new BadRequestException("STOCK_QUANTITY_INVALID", "quantity debe ser un entero distinto de cero");
        }
        return quantity;
    }

    private List<String> normalizeAssetCodes(List<String> assetCodes, int quantity) {
        if (assetCodes == null || assetCodes.size() != quantity) {
            throw new BadRequestException("INDIVIDUAL_ASSET_CODES_INVALID", "asset_codes debe tener la misma cantidad que quantity");
        }

        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String code : assetCodes) {
            String candidate = code == null ? "" : code.trim();
            if (candidate.isEmpty()) {
                throw new BadRequestException("INDIVIDUAL_ASSET_CODE_EMPTY", "asset_codes no puede incluir vacios");
            }
            if (!seen.add(candidate.toLowerCase())) {
                throw new BadRequestException("INDIVIDUAL_ASSET_CODE_DUPLICATE_IN_REQUEST", "asset_codes no puede incluir duplicados");
            }
            normalized.add(candidate);
        }

        return normalized;
    }

    private List<UUID> normalizeIndividualUuids(List<UUID> individualUuids) {
        if (individualUuids == null || individualUuids.isEmpty()) {
            throw new BadRequestException("INDIVIDUAL_IDS_REQUIRED", "individual_uuids es obligatorio para implementos individuales");
        }

        Set<UUID> unique = new HashSet<>();
        for (UUID uuid : individualUuids) {
            if (uuid == null) {
                throw new BadRequestException("INDIVIDUAL_UUID_INVALID", "individual_uuids contiene valores invalidos");
            }
            unique.add(uuid);
        }
        return new ArrayList<>(unique);
    }

    private String normalizeOptionalLiteral(String raw, Set<String> allowed, String code, String message) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!allowed.contains(normalized)) {
            throw new BadRequestException(code, message);
        }
        return normalized;
    }

    private String normalizeOptionalText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private void recordInventoryMovement(UUID implementUuid, MovementAction action, int quantity, String notes) {
        UUID actorUuid = currentUserUuidResolver.resolveCurrentUserUuid()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));
        InventoryMovement movement = new InventoryMovement(
                implementUuid,
                action,
                quantity,
                actorUuid,
                Instant.now(),
                notes
        );
        inventoryMovementRepository.save(movement);
    }

    private boolean isNoFungibleGuardViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("individual_no_fungible")
                        || normalized.contains("trg_guard_individual_no_fungible")
                        || normalized.contains("fn_guard_individual_no_fungible")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
