package com.panol_project.backendpanol.modules.catalog.stock.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static com.panol_project.backendpanol.jooq.tables.Individual.INDIVIDUAL;
import static com.panol_project.backendpanol.jooq.tables.Location.LOCATION;
import static com.panol_project.backendpanol.jooq.tables.Stock.STOCK;

import com.panol_project.backendpanol.jooq.enums.IndividualConditionEnum;
import com.panol_project.backendpanol.jooq.enums.IndividualStatusEnum;
import com.panol_project.backendpanol.jooq.enums.ItemTypeEnum;
import com.panol_project.backendpanol.modules.catalog.stock.domain.IndividualItem;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockCounters;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockItemType;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class StockJooqRepository implements StockRepository {

    private final DSLContext dsl;
    private static final Field<UUID> LOCATION_UUID = DSL.field(DSL.name("location", "uuid"), UUID.class);

    public StockJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ImplementStockContext> findImplementContext(UUID implementUuid) {
        return dsl.select(IMPLEMENT.UUID, LOCATION_UUID, IMPLEMENT.ITEM_TYPE, IMPLEMENT.ACTIVE)
                .from(IMPLEMENT)
                .leftJoin(LOCATION).on(LOCATION.ID.eq(IMPLEMENT.LOCATION_ID))
                .where(IMPLEMENT.UUID.eq(implementUuid))
                .fetchOptional(record -> new ImplementStockContext(
                        record.get(IMPLEMENT.UUID),
                        record.get(LOCATION_UUID),
                        toStockItemType(record.get(IMPLEMENT.ITEM_TYPE)),
                        record.get(IMPLEMENT.ACTIVE)
                ));
    }

    @Override
    public void ensureStockRow(UUID implementUuid) {
        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return;
        }
        dsl.insertInto(STOCK)
                .set(STOCK.IMPLEMENT_ID, implementId)
                .onConflict(STOCK.IMPLEMENT_ID)
                .doNothing()
                .execute();
    }

    @Override
    public Optional<StockCounters> findStockByImplementUuid(UUID implementUuid) {
        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return Optional.empty();
        }
        return dsl.select(STOCK.TOTAL_STOCK, STOCK.MIN_STOCK, STOCK.AVAILABLE, STOCK.RESERVED, STOCK.LOANED, STOCK.DAMAGED)
                .from(STOCK)
                .where(STOCK.IMPLEMENT_ID.eq(implementId))
                .fetchOptional(record -> new StockCounters(
                        record.get(STOCK.TOTAL_STOCK),
                        record.get(STOCK.MIN_STOCK),
                        record.get(STOCK.AVAILABLE),
                        record.get(STOCK.RESERVED),
                        record.get(STOCK.LOANED),
                        record.get(STOCK.DAMAGED)
                ));
    }

    @Override
    public List<IndividualItem> findActiveIndividualsByImplementUuid(UUID implementUuid) {
        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return List.of();
        }

        return dsl.select(
                        INDIVIDUAL.UUID,
                        INDIVIDUAL.ASSET_CODE,
                        INDIVIDUAL.STATUS,
                        INDIVIDUAL.CONDITION,
                        INDIVIDUAL.NOTES,
                        LOCATION_UUID,
                        INDIVIDUAL.ACTIVE
                )
                .from(INDIVIDUAL)
                .leftJoin(LOCATION).on(LOCATION.ID.eq(INDIVIDUAL.CURRENT_LOCATION_ID))
                .where(INDIVIDUAL.IMPLEMENT_ID.eq(implementId).and(INDIVIDUAL.ACTIVE.isTrue()))
                .orderBy(INDIVIDUAL.UUID.asc())
                .fetch(record -> new IndividualItem(
                        record.get(INDIVIDUAL.UUID),
                        implementUuid,
                        record.get(INDIVIDUAL.ASSET_CODE),
                        record.get(INDIVIDUAL.STATUS) == null ? null : record.get(INDIVIDUAL.STATUS).getLiteral(),
                        record.get(INDIVIDUAL.CONDITION) == null ? null : record.get(INDIVIDUAL.CONDITION).getLiteral(),
                        record.get(INDIVIDUAL.NOTES),
                        record.get(LOCATION_UUID),
                        record.get(INDIVIDUAL.ACTIVE)
                ));
    }

    @Override
    public List<IndividualItem> findActiveIndividualsByUuids(UUID implementUuid, List<UUID> individualUuids) {
        if (individualUuids == null || individualUuids.isEmpty()) {
            return List.of();
        }

        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return List.of();
        }

        return dsl.select(
                        INDIVIDUAL.UUID,
                        INDIVIDUAL.ASSET_CODE,
                        INDIVIDUAL.STATUS,
                        INDIVIDUAL.CONDITION,
                        INDIVIDUAL.NOTES,
                        LOCATION_UUID,
                        INDIVIDUAL.ACTIVE
                )
                .from(INDIVIDUAL)
                .leftJoin(LOCATION).on(LOCATION.ID.eq(INDIVIDUAL.CURRENT_LOCATION_ID))
                .where(INDIVIDUAL.IMPLEMENT_ID.eq(implementId)
                        .and(INDIVIDUAL.ACTIVE.isTrue())
                        .and(INDIVIDUAL.UUID.in(individualUuids)))
                .orderBy(INDIVIDUAL.UUID.asc())
                .fetch(record -> new IndividualItem(
                        record.get(INDIVIDUAL.UUID),
                        implementUuid,
                        record.get(INDIVIDUAL.ASSET_CODE),
                        record.get(INDIVIDUAL.STATUS) == null ? null : record.get(INDIVIDUAL.STATUS).getLiteral(),
                        record.get(INDIVIDUAL.CONDITION) == null ? null : record.get(INDIVIDUAL.CONDITION).getLiteral(),
                        record.get(INDIVIDUAL.NOTES),
                        record.get(LOCATION_UUID),
                        record.get(INDIVIDUAL.ACTIVE)
                ));
    }

    @Override
    public void createIndividuals(UUID implementUuid, UUID locationUuid, List<String> assetCodes) {
        if (assetCodes == null || assetCodes.isEmpty()) {
            return;
        }

        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return;
        }
        Long locationId = findLocationIdByUuid(locationUuid);

        var now = OffsetDateTime.now();
        var insert = dsl.insertInto(
                INDIVIDUAL,
                INDIVIDUAL.IMPLEMENT_ID,
                INDIVIDUAL.ASSET_CODE,
                INDIVIDUAL.STATUS,
                INDIVIDUAL.CONDITION,
                INDIVIDUAL.CURRENT_LOCATION_ID,
                INDIVIDUAL.ACTIVE,
                INDIVIDUAL.CREATED_AT,
                INDIVIDUAL.UPDATED_AT
        );

        for (String code : assetCodes) {
            insert = insert.values(
                    implementId,
                    code,
                    IndividualStatusEnum.available,
                    IndividualConditionEnum.good,
                    locationId,
                    true,
                    now,
                    now
            );
        }

        insert.execute();
    }

    @Override
    public void updateStock(UUID implementUuid, int totalDelta, int availableDelta, int reservedDelta, int loanedDelta, int damagedDelta) {
        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return;
        }
        dsl.update(STOCK)
                .set(STOCK.TOTAL_STOCK, STOCK.TOTAL_STOCK.add(totalDelta))
                .set(STOCK.AVAILABLE, STOCK.AVAILABLE.add(availableDelta))
                .set(STOCK.RESERVED, STOCK.RESERVED.add(reservedDelta))
                .set(STOCK.LOANED, STOCK.LOANED.add(loanedDelta))
                .set(STOCK.DAMAGED, STOCK.DAMAGED.add(damagedDelta))
                .set(STOCK.UPDATED_AT, OffsetDateTime.now())
                .where(STOCK.IMPLEMENT_ID.eq(implementId))
                .execute();
    }

    @Override
    public void replaceStock(UUID implementUuid, int total, int available, int reserved, int loaned, int damaged) {
        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return;
        }
        dsl.update(STOCK)
                .set(STOCK.TOTAL_STOCK, total)
                .set(STOCK.AVAILABLE, available)
                .set(STOCK.RESERVED, reserved)
                .set(STOCK.LOANED, loaned)
                .set(STOCK.DAMAGED, damaged)
                .set(STOCK.UPDATED_AT, OffsetDateTime.now())
                .where(STOCK.IMPLEMENT_ID.eq(implementId))
                .execute();
    }

    @Override
    public void updateIndividualsState(
            List<UUID> individualUuids,
            String statusLiteral,
            String conditionLiteral,
            String notes,
            UUID locationUuid,
            Boolean active
    ) {
        if (individualUuids == null || individualUuids.isEmpty()) {
            return;
        }

        var update = dsl.update(INDIVIDUAL)
                .set(INDIVIDUAL.UPDATED_AT, OffsetDateTime.now());

        if (statusLiteral != null) {
            update = update.set(INDIVIDUAL.STATUS, IndividualStatusEnum.lookupLiteral(statusLiteral));
        }
        if (conditionLiteral != null) {
            update = update.set(INDIVIDUAL.CONDITION, IndividualConditionEnum.lookupLiteral(conditionLiteral));
        }
        if (notes != null) {
            update = update.set(INDIVIDUAL.NOTES, notes);
        }
        if (locationUuid != null) {
            Long locationId = findLocationIdByUuid(locationUuid);
            update = update.set(INDIVIDUAL.CURRENT_LOCATION_ID, locationId);
        }
        if (active != null) {
            update = update.set(INDIVIDUAL.ACTIVE, active);
        }

        update.where(INDIVIDUAL.UUID.in(individualUuids)).execute();
    }

    private Long findImplementIdByUuid(UUID implementUuid) {
        if (implementUuid == null) {
            return null;
        }
        return dsl.select(IMPLEMENT.ID)
                .from(IMPLEMENT)
                .where(IMPLEMENT.UUID.eq(implementUuid))
                .fetchOne(IMPLEMENT.ID);
    }

    private Long findLocationIdByUuid(UUID locationUuid) {
        if (locationUuid == null) {
            return null;
        }
        return dsl.select(LOCATION.ID)
                .from(LOCATION)
                .where(LOCATION.UUID.eq(locationUuid))
                .fetchOne(LOCATION.ID);
    }

    private StockItemType toStockItemType(ItemTypeEnum itemType) {
        return itemType == null
                ? null
                : StockItemType.fromLiteral(itemType.getLiteral()).orElse(null);
    }
}
