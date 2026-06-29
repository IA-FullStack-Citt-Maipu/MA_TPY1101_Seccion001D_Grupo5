package com.panol_project.backendpanol.modules.catalog.location.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static com.panol_project.backendpanol.jooq.tables.Individual.INDIVIDUAL;
import static com.panol_project.backendpanol.jooq.tables.Location.LOCATION;

import com.panol_project.backendpanol.modules.catalog.location.domain.LocationOption;
import com.panol_project.backendpanol.modules.catalog.location.domain.LocationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class LocationJooqRepository implements LocationRepository {

    private final DSLContext dsl;

    public LocationJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<LocationOption> findAllActive() {
        return dsl.select(LOCATION.UUID, LOCATION.NAME, LOCATION.DESCRIPTION, LOCATION.ACTIVE)
                .from(LOCATION)
                .where(LOCATION.ACTIVE.eq(true))
                .orderBy(LOCATION.NAME.asc())
                .fetch(record -> new LocationOption(
                        record.get(LOCATION.UUID),
                        record.get(LOCATION.NAME),
                        record.get(LOCATION.DESCRIPTION),
                        record.get(LOCATION.ACTIVE)
                ));
    }

    @Override
    public List<LocationOption> findAll() {
        return dsl.select(LOCATION.UUID, LOCATION.NAME, LOCATION.DESCRIPTION, LOCATION.ACTIVE)
                .from(LOCATION)
                .orderBy(LOCATION.NAME.asc())
                .fetch(record -> new LocationOption(
                        record.get(LOCATION.UUID),
                        record.get(LOCATION.NAME),
                        record.get(LOCATION.DESCRIPTION),
                        record.get(LOCATION.ACTIVE)
                ));
    }

    @Override
    public Optional<LocationOption> findByUuid(UUID uuid) {
        return dsl.select(LOCATION.UUID, LOCATION.NAME, LOCATION.DESCRIPTION, LOCATION.ACTIVE)
                .from(LOCATION)
                .where(LOCATION.UUID.eq(uuid))
                .fetchOptional(record -> new LocationOption(
                        record.get(LOCATION.UUID),
                        record.get(LOCATION.NAME),
                        record.get(LOCATION.DESCRIPTION),
                        record.get(LOCATION.ACTIVE)
                ));
    }

    @Override
    public boolean existsByUuid(UUID uuid) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(LOCATION)
                        .where(LOCATION.UUID.eq(uuid).and(LOCATION.ACTIVE.eq(true)))
        );
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(LOCATION)
                        .where(LOCATION.NAME.equalIgnoreCase(name).and(LOCATION.ACTIVE.eq(true)))
        );
    }

    @Override
    public boolean existsByNameIgnoreCaseAndUuidNot(String name, UUID uuid) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(LOCATION)
                        .where(LOCATION.NAME.equalIgnoreCase(name)
                                .and(LOCATION.UUID.ne(uuid))
                                .and(LOCATION.ACTIVE.eq(true)))
        );
    }

    @Override
    public LocationOption create(String name, String description) {
        UUID uuid = dsl.insertInto(LOCATION)
                .set(LOCATION.NAME, name)
                .set(LOCATION.DESCRIPTION, description)
                .returningResult(LOCATION.UUID)
                .fetchOptional(record -> record.get(LOCATION.UUID))
                .orElseThrow();
        return findByUuid(uuid).orElseThrow();
    }

    @Override
    public LocationOption update(UUID uuid, String name, String description) {
        dsl.update(LOCATION)
                .set(LOCATION.NAME, name)
                .set(LOCATION.DESCRIPTION, description)
                .where(LOCATION.UUID.eq(uuid))
                .execute();
        return findByUuid(uuid).orElseThrow();
    }

    @Override
    public int updateActive(UUID uuid, boolean active) {
        return dsl.update(LOCATION)
                .set(LOCATION.ACTIVE, active)
                .where(LOCATION.UUID.eq(uuid))
                .execute();
    }

    @Override
    public int countImplementAssociationsByLocationUuid(UUID uuid) {
        return dsl.selectCount()
                .from(IMPLEMENT)
                .join(LOCATION).on(LOCATION.ID.eq(IMPLEMENT.LOCATION_ID))
                .where(LOCATION.UUID.eq(uuid))
                .fetchOne(0, int.class);
    }

    @Override
    public int countIndividualAssociationsByLocationUuid(UUID uuid) {
        return dsl.selectCount()
                .from(INDIVIDUAL)
                .join(LOCATION).on(LOCATION.ID.eq(INDIVIDUAL.CURRENT_LOCATION_ID))
                .where(LOCATION.UUID.eq(uuid))
                .fetchOne(0, int.class);
    }

    @Override
    public int deleteByUuid(UUID uuid) {
        return dsl.deleteFrom(LOCATION)
                .where(LOCATION.UUID.eq(uuid))
                .execute();
    }
}
