package com.panol_project.backendpanol.modules.catalog.room.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Room.ROOM;

import com.panol_project.backendpanol.modules.catalog.room.domain.RoomOption;
import com.panol_project.backendpanol.modules.catalog.room.domain.RoomRepository;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class RoomJooqRepository implements RoomRepository {

    private final DSLContext dsl;

    public RoomJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<RoomOption> findAllActive() {
        return dsl.select(ROOM.ID, ROOM.UUID, ROOM.NAME)
                .from(ROOM)
                .where(ROOM.ACTIVE.isTrue())
                .orderBy(ROOM.NAME.asc())
                .fetch(record -> new RoomOption(
                        record.get(ROOM.ID),
                        record.get(ROOM.UUID),
                        record.get(ROOM.NAME)
                ));
    }
}
