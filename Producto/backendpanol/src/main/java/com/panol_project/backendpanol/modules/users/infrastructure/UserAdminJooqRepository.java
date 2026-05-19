package com.panol_project.backendpanol.modules.users.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Role.ROLE;
import static com.panol_project.backendpanol.jooq.tables.User.USER;

import com.panol_project.backendpanol.modules.users.domain.UserAdminRepository;
import com.panol_project.backendpanol.modules.users.domain.UserAdminSummary;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class UserAdminJooqRepository implements UserAdminRepository {

    private final DSLContext dsl;

    public UserAdminJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Long findRoleId(String normalizedRole) {
        String roleKey = roleKey(normalizedRole);
        return dsl.select(ROLE.ID)
                .from(ROLE)
                .where(ROLE.NAME.equalIgnoreCase(normalizedRole)
                        .or(ROLE.NAME.likeIgnoreCase('%' + roleKey + '%')))
                .orderBy(
                        DSL.when(ROLE.NAME.equalIgnoreCase(normalizedRole), DSL.inline(0)).otherwise(DSL.inline(1)),
                        ROLE.NAME.asc()
                )
                .limit(1)
                .fetchOne(ROLE.ID);
    }

    @Override
    public int countUsersByRutOrEmail(String normalizedRut, String normalizedEmail) {
        var duplicateCondition = USER.RUT.eq(normalizedRut);
        if (normalizedEmail != null) {
            duplicateCondition = duplicateCondition.or(USER.EMAIL.eq(normalizedEmail));
        }
        Integer duplicated = dsl.selectCount().from(USER)
                .where(duplicateCondition)
                .fetchOne(0, Integer.class);
        return duplicated == null ? 0 : duplicated;
    }

    @Override
    public int countUsersByRutOrEmailExcludingUser(String normalizedRut, String normalizedEmail, UUID userUuid) {
        var duplicateCondition = USER.RUT.eq(normalizedRut);
        if (normalizedEmail != null) {
            duplicateCondition = duplicateCondition.or(USER.EMAIL.eq(normalizedEmail));
        }
        Integer duplicated = dsl.selectCount()
                .from(USER)
                .where(duplicateCondition)
                .and(USER.UUID.ne(userUuid))
                .fetchOne(0, Integer.class);
        return duplicated == null ? 0 : duplicated;
    }

    @Override
    public void createUser(String name, String rut, String email, String passwordHash, Long roleId, boolean active) {
        dsl.insertInto(USER)
                .set(USER.NAME, name)
                .set(USER.RUT, rut)
                .set(USER.EMAIL, email)
                .set(USER.PASSWORD_HASH, passwordHash)
                .set(USER.ROLE_ID, roleId)
                .set(USER.ACTIVE, active)
                .execute();
    }

    @Override
    public int updateUserRole(UUID userUuid, Long roleId) {
        return dsl.update(USER)
                .set(USER.ROLE_ID, roleId)
                .where(USER.UUID.eq(userUuid))
                .execute();
    }

    @Override
    public List<UserAdminSummary> listUsers() {
        return dsl.select(
                        USER.UUID,
                        USER.NAME,
                        USER.RUT,
                        USER.EMAIL,
                        ROLE.NAME,
                        USER.ACTIVE,
                        USER.CREATED_AT)
                .from(USER)
                .join(ROLE).on(ROLE.ID.eq(USER.ROLE_ID))
                .orderBy(USER.CREATED_AT.desc())
                .fetch(record -> new UserAdminSummary(
                        record.value1() == null ? null : record.value1().toString(),
                        record.value2(),
                        record.value3(),
                        record.value4(),
                        record.value5(),
                        Boolean.TRUE.equals(record.value6()),
                        record.value7()));
    }

    @Override
    public int updateUserActive(UUID userUuid, boolean active) {
        return dsl.update(USER)
                .set(USER.ACTIVE, active)
                .where(USER.UUID.eq(userUuid))
                .execute();
    }

    @Override
    public boolean existsUserByUuid(UUID userUuid) {
        Integer count = dsl.selectCount()
                .from(USER)
                .where(USER.UUID.eq(userUuid))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public int updateUser(UUID userUuid, String name, String rut, String email) {
        return dsl.update(USER)
                .set(USER.NAME, name)
                .set(USER.RUT, rut)
                .set(USER.EMAIL, email)
                .where(USER.UUID.eq(userUuid))
                .execute();
    }

    private String roleKey(String normalizedRole) {
        return switch (normalizedRole) {
            case "COORDINADOR" -> "COORD";
            default -> normalizedRole;
        };
    }
}
