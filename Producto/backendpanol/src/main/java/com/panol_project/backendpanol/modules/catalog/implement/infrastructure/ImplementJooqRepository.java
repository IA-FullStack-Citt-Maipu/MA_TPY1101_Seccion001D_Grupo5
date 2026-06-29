package com.panol_project.backendpanol.modules.catalog.implement.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Category.CATEGORY;
import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static com.panol_project.backendpanol.jooq.tables.Individual.INDIVIDUAL;
import static com.panol_project.backendpanol.jooq.tables.Location.LOCATION;
import static com.panol_project.backendpanol.jooq.tables.Stock.STOCK;

import com.panol_project.backendpanol.jooq.enums.ItemTypeEnum;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementCategorySummary;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementItemType;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementLocationSummary;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementRepository;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementStockSummary;
import com.panol_project.backendpanol.modules.catalog.implement.domain.ImplementSummary;
import com.panol_project.backendpanol.modules.catalog.implement.domain.Implemento;
import com.panol_project.backendpanol.modules.catalog.implement.domain.StockStatusFilter;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class ImplementJooqRepository implements ImplementRepository {

    private final DSLContext dsl;

    private static final Field<UUID> CATEGORY_UUID = DSL.field(DSL.name("category", "uuid"), UUID.class);
    private static final Field<UUID> LOCATION_UUID = DSL.field(DSL.name("location", "uuid"), UUID.class);
    private static final Field<String[]> INDIVIDUAL_ASSET_CODES = DSL.field(
            "coalesce((" +
                    "select array_agg(i.asset_code order by i.asset_code) " +
                    "from public.individual i " +
                    "where i.implement_id = {0} and i.active = true" +
                    "), array[]::text[])",
            String[].class,
            IMPLEMENT.ID
    ).as("individual_asset_codes");

    public ImplementJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<Implemento> findByUuid(UUID uuid) {
        return dsl.select(
                        IMPLEMENT.UUID,
                        IMPLEMENT.NAME,
                        IMPLEMENT.DESCRIPTION,
                        CATEGORY_UUID,
                        LOCATION_UUID,
                        IMPLEMENT.ITEM_TYPE,
                        IMPLEMENT.BARCODE,
                        IMPLEMENT.IMG_URL,
                        IMPLEMENT.OBSERVATIONS,
                        IMPLEMENT.ACTIVE,
                        IMPLEMENT.CREATED_AT,
                        IMPLEMENT.UPDATED_AT
                )
                .from(IMPLEMENT)
                .leftJoin(CATEGORY).on(CATEGORY.ID.eq(IMPLEMENT.CATEGORY_ID))
                .leftJoin(LOCATION).on(LOCATION.ID.eq(IMPLEMENT.LOCATION_ID))
                .where(IMPLEMENT.UUID.eq(uuid))
                .fetchOptional(this::toDomain);
    }

    @Override
    public Optional<ImplementSummary> findSummaryByUuid(UUID uuid) {
                return dsl.select(
                        IMPLEMENT.UUID,
                        IMPLEMENT.NAME,
                        IMPLEMENT.DESCRIPTION,
                        IMPLEMENT.BARCODE,
                        INDIVIDUAL_ASSET_CODES,
                        IMPLEMENT.IMG_URL,
                        IMPLEMENT.ACTIVE,
                        IMPLEMENT.ITEM_TYPE,
                        CATEGORY_UUID,
                        CATEGORY.NAME,
                        CATEGORY.ACTIVE,
                        LOCATION_UUID,
                        LOCATION.NAME,
                        LOCATION.DESCRIPTION,
                        STOCK.TOTAL_STOCK,
                        STOCK.MIN_STOCK,
                        STOCK.AVAILABLE,
                        STOCK.RESERVED,
                        STOCK.LOANED,
                        STOCK.DAMAGED
                )
                .from(IMPLEMENT)
                .leftJoin(CATEGORY).on(CATEGORY.ID.eq(IMPLEMENT.CATEGORY_ID))
                .leftJoin(LOCATION).on(LOCATION.ID.eq(IMPLEMENT.LOCATION_ID))
                .leftJoin(STOCK).on(STOCK.IMPLEMENT_ID.eq(IMPLEMENT.ID))
                .where(IMPLEMENT.UUID.eq(uuid))
                .fetchOptional(this::toSummary);
    }

    @Override
    public List<ImplementSummary> findAllSummaries(
            String name,
            UUID categoryUuid,
            StockStatusFilter stockStatusFilter,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            UUID excludeLoanUuid
    ) {
        Condition condition = IMPLEMENT.ACTIVE.isTrue();

        if (name != null) {
            String normalizedQuery = name.toLowerCase(Locale.ROOT);
            String likeQuery = "%" + normalizedQuery + "%";
            Condition matchesName = DSL.lower(IMPLEMENT.NAME).like(likeQuery);
            Condition matchesBarcode = IMPLEMENT.BARCODE.isNotNull()
                    .and(DSL.lower(IMPLEMENT.BARCODE).like(likeQuery));
            Condition matchesIndividualAssetCode = DSL.exists(
                    DSL.selectOne()
                            .from(INDIVIDUAL)
                            .where(INDIVIDUAL.IMPLEMENT_ID.eq(IMPLEMENT.ID)
                                    .and(INDIVIDUAL.ACTIVE.isTrue())
                                    .and(DSL.lower(INDIVIDUAL.ASSET_CODE).like(likeQuery)))
            );

            condition = condition.and(matchesName.or(matchesBarcode).or(matchesIndividualAssetCode));
        }

        if (categoryUuid != null) {
            condition = condition.and(CATEGORY_UUID.eq(categoryUuid));
        }

        if (stockStatusFilter != null && scheduledAt == null) {
            condition = condition.and(resolveStockField(stockStatusFilter).gt(0));
        }

        if (scheduledAt == null) {
                return dsl.select(
                        IMPLEMENT.UUID,
                        IMPLEMENT.NAME,
                        IMPLEMENT.DESCRIPTION,
                        IMPLEMENT.BARCODE,
                        INDIVIDUAL_ASSET_CODES,
                        IMPLEMENT.IMG_URL,
                        IMPLEMENT.ACTIVE,
                        IMPLEMENT.ITEM_TYPE,
                        CATEGORY_UUID,
                        CATEGORY.NAME,
                        CATEGORY.ACTIVE,
                        LOCATION_UUID,
                        LOCATION.NAME,
                        LOCATION.DESCRIPTION,
                        STOCK.TOTAL_STOCK,
                        STOCK.MIN_STOCK,
                        STOCK.AVAILABLE,
                        STOCK.RESERVED,
                        STOCK.LOANED,
                        STOCK.DAMAGED
                )
                .from(IMPLEMENT)
                .leftJoin(CATEGORY).on(CATEGORY.ID.eq(IMPLEMENT.CATEGORY_ID))
                .leftJoin(LOCATION).on(LOCATION.ID.eq(IMPLEMENT.LOCATION_ID))
                .leftJoin(STOCK).on(STOCK.IMPLEMENT_ID.eq(IMPLEMENT.ID))
                .where(condition)
                .orderBy(IMPLEMENT.NAME.asc())
                .fetch(this::toSummary);
        }

        OffsetDateTime effectiveExpectedReturnAt = expectedReturnAt != null ? expectedReturnAt : scheduledAt.plusHours(2);
        Long excludeLoanId = findLoanIdByUuid(excludeLoanUuid);
        Field<Integer> availableInRange = DSL.field(
                        "coalesce((select availability.available_quantity " +
                                "from public.fn_get_implement_availability({0}, {1}, {2}, false, {3}) as availability), 0)",
                        Integer.class,
                        IMPLEMENT.ID,
                        DSL.val(scheduledAt),
                        DSL.val(effectiveExpectedReturnAt),
                        DSL.val(excludeLoanId, Long.class)
                )
                .as("available_in_range");

        if (stockStatusFilter == StockStatusFilter.AVAILABLE) {
            condition = condition.and(availableInRange.gt(0));
        }

        List<ImplementSummary> rangedSummaries = dsl.select(
                        IMPLEMENT.UUID,
                        IMPLEMENT.NAME,
                        IMPLEMENT.DESCRIPTION,
                        IMPLEMENT.BARCODE,
                        INDIVIDUAL_ASSET_CODES,
                        IMPLEMENT.IMG_URL,
                        IMPLEMENT.ACTIVE,
                        IMPLEMENT.ITEM_TYPE,
                        CATEGORY_UUID,
                        CATEGORY.NAME,
                        CATEGORY.ACTIVE,
                        LOCATION_UUID,
                        LOCATION.NAME,
                        LOCATION.DESCRIPTION,
                        STOCK.TOTAL_STOCK,
                        STOCK.MIN_STOCK,
                        STOCK.AVAILABLE,
                        STOCK.RESERVED,
                        STOCK.LOANED,
                        STOCK.DAMAGED,
                        availableInRange
                )
                .from(IMPLEMENT)
                .leftJoin(CATEGORY).on(CATEGORY.ID.eq(IMPLEMENT.CATEGORY_ID))
                .leftJoin(LOCATION).on(LOCATION.ID.eq(IMPLEMENT.LOCATION_ID))
                .leftJoin(STOCK).on(STOCK.IMPLEMENT_ID.eq(IMPLEMENT.ID))
                .where(condition)
                .orderBy(IMPLEMENT.NAME.asc())
                .fetch(record -> toSummary(record, availableInRange));

        if (stockStatusFilter == StockStatusFilter.AVAILABLE) {
            return rangedSummaries;
        }

        return rangedSummaries;
    }

    @Override
    public boolean existsActiveByNameIgnoreCase(String nombre, UUID categoriaUuid) {
        Condition byName = IMPLEMENT.NAME.equalIgnoreCase(nombre);
        Condition byCategory = resolveCategoryCondition(categoriaUuid);
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(IMPLEMENT)
                        .where(IMPLEMENT.ACTIVE.isTrue().and(byName).and(byCategory))
        );
    }

    @Override
    public boolean existsActiveByNameIgnoreCaseAndUuidNot(String nombre, UUID categoriaUuid, UUID excludedUuid) {
        Condition byName = IMPLEMENT.NAME.equalIgnoreCase(nombre);
        Condition byCategory = resolveCategoryCondition(categoriaUuid);
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(IMPLEMENT)
                        .where(IMPLEMENT.ACTIVE.isTrue()
                                .and(byName)
                                .and(byCategory)
                                .and(IMPLEMENT.UUID.ne(excludedUuid)))
        );
    }

    @Override
    public Implemento create(
            String nombre,
            String descripcion,
            UUID categoriaUuid,
            UUID locationUuid,
            ImplementItemType itemType,
            String barcode,
            String imgUrl,
            String observations
    ) {
        Long categoryId = findCategoryIdByUuid(categoriaUuid);
        Long locationId = findLocationIdByUuid(locationUuid);

        UUID createdUuid = dsl.insertInto(IMPLEMENT)
                .set(IMPLEMENT.NAME, nombre)
                .set(IMPLEMENT.DESCRIPTION, descripcion)
                .set(IMPLEMENT.CATEGORY_ID, categoryId)
                .set(IMPLEMENT.LOCATION_ID, locationId)
                .set(IMPLEMENT.ITEM_TYPE, toJooqItemType(itemType))
                .set(IMPLEMENT.BARCODE, barcode)
                .set(IMPLEMENT.IMG_URL, imgUrl)
                .set(IMPLEMENT.OBSERVATIONS, observations)
                .returningResult(IMPLEMENT.UUID)
                .fetchOptional(record -> record.get(IMPLEMENT.UUID))
                .orElseThrow();

        return findByUuid(createdUuid).orElseThrow();
    }

    @Override
    public Implemento update(
            UUID uuid,
            String nombre,
            String descripcion,
            UUID categoriaUuid,
            UUID locationUuid,
            ImplementItemType itemType,
            String barcode,
            String imgUrl,
            String observations
    ) {
        Long categoryId = findCategoryIdByUuid(categoriaUuid);
        Long locationId = findLocationIdByUuid(locationUuid);

        dsl.update(IMPLEMENT)
                .set(IMPLEMENT.NAME, nombre)
                .set(IMPLEMENT.DESCRIPTION, descripcion)
                .set(IMPLEMENT.CATEGORY_ID, categoryId)
                .set(IMPLEMENT.LOCATION_ID, locationId)
                .set(IMPLEMENT.ITEM_TYPE, toJooqItemType(itemType))
                .set(IMPLEMENT.BARCODE, barcode)
                .set(IMPLEMENT.IMG_URL, imgUrl)
                .set(IMPLEMENT.OBSERVATIONS, observations)
                .set(IMPLEMENT.UPDATED_AT, OffsetDateTime.now())
                .where(IMPLEMENT.UUID.eq(uuid))
                .execute();

        return findByUuid(uuid).orElseThrow();
    }

    @Override
    public int updateMinStockByImplementUuid(UUID implementUuid, Integer minStock) {
        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return 0;
        }
        return dsl.insertInto(STOCK)
                .set(STOCK.IMPLEMENT_ID, implementId)
                .set(STOCK.MIN_STOCK, minStock)
                .onConflict(STOCK.IMPLEMENT_ID)
                .doUpdate()
                .set(STOCK.MIN_STOCK, minStock)
                .execute();
    }

    @Override
    public Optional<Integer> findMinStockByImplementUuid(UUID implementUuid) {
        Long implementId = findImplementIdByUuid(implementUuid);
        if (implementId == null) {
            return Optional.empty();
        }

        return dsl.select(STOCK.MIN_STOCK)
                .from(STOCK)
                .where(STOCK.IMPLEMENT_ID.eq(implementId))
                .fetchOptional(STOCK.MIN_STOCK);
    }

    @Override
    public int updateActive(UUID uuid, boolean active) {
        return dsl.update(IMPLEMENT)
                .set(IMPLEMENT.ACTIVE, active)
                .set(IMPLEMENT.UPDATED_AT, OffsetDateTime.now())
                .where(IMPLEMENT.UUID.eq(uuid))
                .execute();
    }

    private Field<Integer> resolveStockField(StockStatusFilter filter) {
        return switch (filter) {
            case AVAILABLE -> STOCK.AVAILABLE;
            case RESERVED -> STOCK.RESERVED;
            case LOANED -> STOCK.LOANED;
            case DAMAGED -> STOCK.DAMAGED;
        };
    }

    private ImplementSummary toSummary(Record record) {
        return toSummary(record, null);
    }

    private ImplementSummary toSummary(Record record, Field<Integer> availableOverride) {
        UUID categoryUuid = record.get(CATEGORY_UUID);
        ImplementCategorySummary category = categoryUuid == null
                ? null
                : new ImplementCategorySummary(
                        categoryUuid,
                        record.get(CATEGORY.NAME),
                        record.get(CATEGORY.ACTIVE)
                );

        UUID locationUuid = record.get(LOCATION_UUID);
        ImplementLocationSummary location = locationUuid == null
                ? null
                : new ImplementLocationSummary(
                        locationUuid,
                        record.get(LOCATION.NAME),
                        record.get(LOCATION.DESCRIPTION)
                );

        Integer available = availableOverride == null
                ? record.get(STOCK.AVAILABLE)
                : record.get(availableOverride);
        String[] individualAssetCodes = record.get(INDIVIDUAL_ASSET_CODES);

        return new ImplementSummary(
                record.get(IMPLEMENT.UUID),
                record.get(IMPLEMENT.NAME),
                record.get(IMPLEMENT.DESCRIPTION),
                record.get(IMPLEMENT.BARCODE),
                individualAssetCodes == null
                        ? List.of()
                        : Arrays.stream(individualAssetCodes)
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(value -> !value.isEmpty())
                                .toList(),
                record.get(IMPLEMENT.IMG_URL),
                record.get(IMPLEMENT.ACTIVE),
                toDomainItemType(record.get(IMPLEMENT.ITEM_TYPE)),
                category,
                location,
                new ImplementStockSummary(
                        record.get(STOCK.TOTAL_STOCK),
                        record.get(STOCK.MIN_STOCK),
                        available,
                        record.get(STOCK.RESERVED),
                        record.get(STOCK.LOANED),
                        record.get(STOCK.DAMAGED)
                )
        );
    }

    private Implemento toDomain(Record record) {
        return new Implemento(
                record.get(IMPLEMENT.UUID),
                record.get(IMPLEMENT.NAME),
                record.get(IMPLEMENT.DESCRIPTION),
                record.get(CATEGORY_UUID),
                record.get(LOCATION_UUID),
                toDomainItemType(record.get(IMPLEMENT.ITEM_TYPE)),
                record.get(IMPLEMENT.BARCODE),
                record.get(IMPLEMENT.IMG_URL),
                record.get(IMPLEMENT.OBSERVATIONS),
                record.get(IMPLEMENT.ACTIVE),
                record.get(IMPLEMENT.CREATED_AT),
                record.get(IMPLEMENT.UPDATED_AT)
        );
    }

    private Condition resolveCategoryCondition(UUID categoriaUuid) {
        if (categoriaUuid == null) {
            return IMPLEMENT.CATEGORY_ID.isNull();
        }
        Long categoryId = findCategoryIdByUuid(categoriaUuid);
        if (categoryId == null) {
            return DSL.falseCondition();
        }
        return IMPLEMENT.CATEGORY_ID.eq(categoryId);
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

    private Long findCategoryIdByUuid(UUID categoryUuid) {
        if (categoryUuid == null) {
            return null;
        }
        return dsl.select(CATEGORY.ID)
                .from(CATEGORY)
                .where(CATEGORY.UUID.eq(categoryUuid))
                .fetchOne(CATEGORY.ID);
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

    private Long findLoanIdByUuid(UUID loanUuid) {
        if (loanUuid == null) {
            return null;
        }
        Record record = dsl.fetchOne("select id from public.loan where uuid = ?::uuid", loanUuid);
        return record == null ? null : record.get("id", Long.class);
    }

    private ImplementItemType toDomainItemType(ItemTypeEnum itemType) {
        return itemType == null
                ? null
                : ImplementItemType.fromLiteral(itemType.getLiteral()).orElse(null);
    }

    private ItemTypeEnum toJooqItemType(ImplementItemType itemType) {
        return ItemTypeEnum.lookupLiteral(Objects.requireNonNull(itemType, "itemType").literal());
    }
}
