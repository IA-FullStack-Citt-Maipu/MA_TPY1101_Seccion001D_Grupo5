package com.panol_project.backendpanol.modules.users.infrastructure;

import com.panol_project.backendpanol.modules.users.domain.UserRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.panol_project.backendpanol.jooq.tables.User.USER;

@Repository
public class UserJooqRepository implements UserRepository {

    private final DSLContext dsl;

    public UserJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Map<UUID, String> findNamesByUuids(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return Collections.emptyMap();
        }
        return dsl.select(USER.UUID, USER.NAME)
                .from(USER)
                .where(USER.UUID.in(uuids))
                .fetch()
                .intoMap(USER.UUID, USER.NAME);
    }
}
