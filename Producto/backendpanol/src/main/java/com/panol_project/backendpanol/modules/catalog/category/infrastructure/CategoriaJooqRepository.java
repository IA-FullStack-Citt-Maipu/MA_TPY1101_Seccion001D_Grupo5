package com.panol_project.backendpanol.modules.catalog.category.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Category.CATEGORY;
import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static org.jooq.impl.DSL.lower;
import static org.jooq.impl.DSL.noCondition;

import com.panol_project.backendpanol.modules.catalog.category.domain.Categoria;
import com.panol_project.backendpanol.modules.catalog.category.domain.CategoriaRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class CategoriaJooqRepository implements CategoriaRepository {

    private final DSLContext dsl;

    public CategoriaJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Categoria> findAll(boolean includeInactive) {
        Condition condition = includeInactive ? noCondition() : CATEGORY.ACTIVE.isTrue();

        return dsl.select(CATEGORY.UUID, CATEGORY.NAME, CATEGORY.DESCRIPTION, CATEGORY.ACTIVE, CATEGORY.CREATED_AT)
                .from(CATEGORY)
                .where(condition)
                .orderBy(CATEGORY.NAME.asc())
                .fetch(this::toDomain);
    }

    @Override
    public Optional<Categoria> findByUuid(UUID uuid) {
        return dsl.select(CATEGORY.UUID, CATEGORY.NAME, CATEGORY.DESCRIPTION, CATEGORY.ACTIVE, CATEGORY.CREATED_AT)
                .from(CATEGORY)
                .where(CATEGORY.UUID.eq(uuid))
                .fetchOptional()
                .map(this::toDomain);
    }

    @Override
    public boolean existsByNombre(String nombre, UUID excludingUuid) {
        Condition condition = lower(CATEGORY.NAME).eq(nombre.toLowerCase(Locale.ROOT));
        if (excludingUuid != null) {
            condition = condition.and(CATEGORY.UUID.ne(excludingUuid));
        }

        return dsl.fetchExists(dsl.selectOne().from(CATEGORY).where(condition));
    }

    @Override
    public Optional<Categoria> findActiveByUuid(UUID uuid) {
        return dsl.select(CATEGORY.UUID, CATEGORY.NAME, CATEGORY.DESCRIPTION, CATEGORY.ACTIVE, CATEGORY.CREATED_AT)
                .from(CATEGORY)
                .where(CATEGORY.UUID.eq(uuid).and(CATEGORY.ACTIVE.isTrue()))
                .fetchOptional()
                .map(this::toDomain);
    }

    @Override
    public Categoria create(String nombre, String descripcion) {
        return dsl.insertInto(CATEGORY)
                .set(CATEGORY.NAME, nombre)
                .set(CATEGORY.DESCRIPTION, descripcion)
                .set(CATEGORY.ACTIVE, true)
                .returningResult(CATEGORY.UUID, CATEGORY.NAME, CATEGORY.DESCRIPTION, CATEGORY.ACTIVE, CATEGORY.CREATED_AT)
                .fetchOptional()
                .map(this::toDomain)
                .orElseThrow();
    }

    @Override
    public Categoria updateNombre(UUID uuid, String nombre, String descripcion) {
        return dsl.update(CATEGORY)
                .set(CATEGORY.NAME, nombre)
                .set(CATEGORY.DESCRIPTION, descripcion)
                .where(CATEGORY.UUID.eq(uuid))
                .returningResult(CATEGORY.UUID, CATEGORY.NAME, CATEGORY.DESCRIPTION, CATEGORY.ACTIVE, CATEGORY.CREATED_AT)
                .fetchOptional()
                .map(this::toDomain)
                .orElseThrow();
    }

    @Override
    public void deactivate(UUID uuid) {
        dsl.update(CATEGORY)
                .set(CATEGORY.ACTIVE, false)
                .where(CATEGORY.UUID.eq(uuid))
                .execute();
    }

    @Override
    public void activate(UUID uuid) {
        dsl.update(CATEGORY)
                .set(CATEGORY.ACTIVE, true)
                .where(CATEGORY.UUID.eq(uuid))
                .execute();
    }

    @Override
    public void deleteByUuid(UUID uuid) {
        dsl.deleteFrom(CATEGORY)
                .where(CATEGORY.UUID.eq(uuid))
                .execute();
    }

    @Override
    public int countImplementsByCategoryUuid(UUID categoryUuid) {
        Long categoryId = findCategoryIdByUuid(categoryUuid);
        if (categoryId == null) {
            return 0;
        }
        return dsl.fetchCount(
                dsl.selectOne()
                        .from(IMPLEMENT)
                        .where(IMPLEMENT.CATEGORY_ID.eq(categoryId))
        );
    }

    @Override
    public int countActiveImplementsByCategoryUuid(UUID categoryUuid) {
        Long categoryId = findCategoryIdByUuid(categoryUuid);
        if (categoryId == null) {
            return 0;
        }
        return dsl.fetchCount(
                dsl.selectOne()
                        .from(IMPLEMENT)
                        .where(IMPLEMENT.CATEGORY_ID.eq(categoryId)
                                .and(IMPLEMENT.ACTIVE.isTrue()))
        );
    }

    private Categoria toDomain(Record record) {
        return new Categoria(
                record.get(CATEGORY.UUID),
                record.get(CATEGORY.NAME),
                record.get(CATEGORY.DESCRIPTION),
                record.get(CATEGORY.ACTIVE),
                record.get(CATEGORY.CREATED_AT)
        );
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
}
