package com.panol_project.backendpanol.modules.loan.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Implement.IMPLEMENT;
import static com.panol_project.backendpanol.jooq.tables.InventoryMovement.INVENTORY_MOVEMENT;
import static com.panol_project.backendpanol.jooq.tables.Loan.LOAN;
import static com.panol_project.backendpanol.jooq.tables.LoanDetail.LOAN_DETAIL;
import static com.panol_project.backendpanol.jooq.tables.LoanStatusHistory.LOAN_STATUS_HISTORY;
import static com.panol_project.backendpanol.jooq.tables.Notification.NOTIFICATION;
import static com.panol_project.backendpanol.jooq.tables.OutboxEvent.OUTBOX_EVENT;
import static com.panol_project.backendpanol.jooq.tables.OutboxEvents.OUTBOX_EVENTS;
import static com.panol_project.backendpanol.jooq.tables.Role.ROLE;
import static com.panol_project.backendpanol.jooq.tables.Room.ROOM;
import static com.panol_project.backendpanol.jooq.tables.Stock.STOCK;
import static com.panol_project.backendpanol.jooq.tables.Subject.SUBJECT;
import static com.panol_project.backendpanol.jooq.tables.User.USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.panol_project.backendpanol.jooq.enums.ItemTypeEnum;
import com.panol_project.backendpanol.jooq.enums.LoanStatusEnum;
import com.panol_project.backendpanol.modules.loan.application.SolicitarPrestamoUseCase;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.outbox.worker-delay-ms=600000",
        "app.outbox.metrics-delay-ms=600000",
        "spring.datasource.url=${JOOQ_DB_URL:jdbc:postgresql://127.0.0.1:5432/panol_ci}",
        "spring.datasource.username=${JOOQ_DB_USER:panol_ci}",
        "spring.datasource.password=${JOOQ_DB_PASSWORD:panol_ci}"
})
@ActiveProfiles("docker")
class SolicitarPrestamoPersistenceTest {

    private static final UUID SYSTEM_OUTBOX_USER_UUID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private SolicitarPrestamoUseCase solicitarPrestamoUseCase;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private final List<UUID> loanUuidsToCleanup = new ArrayList<>();
    private final List<UUID> userUuidsToCleanup = new ArrayList<>();
    private final List<UUID> roomUuidsToCleanup = new ArrayList<>();
    private final List<UUID> subjectUuidsToCleanup = new ArrayList<>();
    private final List<UUID> implementUuidsToCleanup = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanupLoans();
        cleanupImplements();
        cleanupSubjects();
        cleanupRooms();
        cleanupUsers();
    }

    @Test
    void solicitarDebePersistirLoanDetallesHistorialYNotificacionesEnUnaSolaTransaccion() {
        UUID requesterUuid = insertUser("docente", "Docente Persistencia");
        UUID coordinatorUuid = insertUser("coordinador", "Coordinador Persistencia");
        UUID roomUuid = insertRoom();
        UUID subjectUuid = insertSubject();
        UUID implementUuid = insertImplement(10);

        int expectedNotificationRecipients = countActiveHumanCoordinators();
        String expectedMessage = notificationMessage("Docente Persistencia");
        String expectedTitle = notificationTitle();
        int notificationsBefore = notificationCount(expectedTitle, expectedMessage);
        Long systemOutboxUserId = findUserId(SYSTEM_OUTBOX_USER_UUID);
        int systemNotificationsBefore = userNotificationCount(systemOutboxUserId, expectedTitle, expectedMessage);
        setAuthenticatedUser(requesterUuid, "DOCENTE");

        LoanSummaryView created = solicitarPrestamoUseCase.solicitar(new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                subjectUuid,
                OffsetDateTime.parse("2026-06-22T10:15:00-04:00"),
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 3))
        ));
        loanUuidsToCleanup.add(created.uuid());
        SecurityContextHolder.clearContext();

        assertNotNull(created);

        Long loanId = dsl.select(LOAN.ID)
                .from(LOAN)
                .where(LOAN.UUID.eq(created.uuid()))
                .fetchOne(LOAN.ID);
        assertNotNull(loanId);

        Long requesterId = findUserId(requesterUuid);
        Long coordinatorId = findUserId(coordinatorUuid);
        Long implementId = findImplementId(implementUuid);

        assertEquals(1, dsl.fetchCount(LOAN, LOAN.UUID.eq(created.uuid())));
        assertEquals(1, dsl.fetchCount(LOAN_DETAIL, LOAN_DETAIL.LOAN_ID.eq(loanId)));

        var history = dsl.select(
                        LOAN_STATUS_HISTORY.ACTOR_USER_ID,
                        LOAN_STATUS_HISTORY.FROM_STATUS,
                        LOAN_STATUS_HISTORY.TO_STATUS,
                        LOAN_STATUS_HISTORY.NOTES
                )
                .from(LOAN_STATUS_HISTORY)
                .where(LOAN_STATUS_HISTORY.LOAN_ID.eq(loanId))
                .fetchOne();

        assertNotNull(history);
        assertEquals(requesterId, history.get(LOAN_STATUS_HISTORY.ACTOR_USER_ID));
        assertNull(history.get(LOAN_STATUS_HISTORY.FROM_STATUS));
        assertEquals(LoanStatusEnum.pending, history.get(LOAN_STATUS_HISTORY.TO_STATUS));
        assertEquals("Solicitud creada", history.get(LOAN_STATUS_HISTORY.NOTES));

        assertEquals(expectedNotificationRecipients, notificationCount(expectedTitle, expectedMessage) - notificationsBefore);

        assertEquals(1, dsl.selectCount()
                .from(NOTIFICATION)
                .where(NOTIFICATION.USER_ID.eq(coordinatorId)
                        .and(NOTIFICATION.TITLE.eq(expectedTitle))
                        .and(NOTIFICATION.MESSAGE.eq(expectedMessage)))
                .fetchOne(0, Integer.class));

        assertEquals(systemNotificationsBefore, userNotificationCount(systemOutboxUserId, expectedTitle, expectedMessage));

        assertEquals(1, dsl.selectCount()
                .from(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.AGGREGATE_ID.eq(created.uuid()))
                .fetchOne(0, Integer.class));
        assertEquals(1, dsl.selectCount()
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.AGGREGATE_ID.eq(created.uuid()))
                .fetchOne(0, Integer.class));
        assertEquals(1, dsl.selectCount()
                .from(STOCK)
                .where(STOCK.IMPLEMENT_ID.eq(implementId))
                .fetchOne(0, Integer.class));
        assertEquals(0, dsl.selectCount()
                .from(INVENTORY_MOVEMENT)
                .where(INVENTORY_MOVEMENT.IMPLEMENT_ID.eq(implementId))
                .fetchOne(0, Integer.class));
    }

    @Test
    void solicitarDebeHacerRollbackCompletoSiLaTransaccionExternaFallaDespuesDePersistir() {
        UUID requesterUuid = insertUser("docente", "Docente Rollback");
        UUID coordinatorUuid = insertUser("coordinador", "Coordinador Rollback");
        UUID roomUuid = insertRoom();
        UUID subjectUuid = insertSubject();
        UUID implementUuid = insertImplement(10);

        String expectedMessage = notificationMessage("Docente Rollback");
        int notificationsBefore = notificationCount(notificationTitle(), expectedMessage);
        setAuthenticatedUser(requesterUuid, "DOCENTE");

        final UUID[] createdLoanUuid = new UUID[1];
        RuntimeException forced = assertThrows(RuntimeException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    LoanSummaryView created = solicitarPrestamoUseCase.solicitar(new SolicitarPrestamoCommand(
                            requesterUuid,
                            roomUuid,
                            subjectUuid,
                            OffsetDateTime.parse("2026-06-23T11:00:00-04:00"),
                            null,
                            List.of(new SolicitarPrestamoItemCommand(implementUuid, 1))
                    ));
                    createdLoanUuid[0] = created.uuid();
                    throw new RuntimeException("force rollback");
                })
        );
        SecurityContextHolder.clearContext();

        assertEquals("force rollback", forced.getMessage());
        assertNotNull(createdLoanUuid[0]);

        assertEquals(0, dsl.fetchCount(LOAN, LOAN.UUID.eq(createdLoanUuid[0])));
        assertEquals(0, dsl.selectCount()
                .from(LOAN_STATUS_HISTORY)
                .join(LOAN).on(LOAN.ID.eq(LOAN_STATUS_HISTORY.LOAN_ID))
                .where(LOAN.UUID.eq(createdLoanUuid[0]))
                .fetchOne(0, Integer.class));
        assertEquals(notificationsBefore, notificationCount(notificationTitle(), expectedMessage));
        assertEquals(0, dsl.selectCount()
                .from(INVENTORY_MOVEMENT)
                .join(IMPLEMENT).on(IMPLEMENT.ID.eq(INVENTORY_MOVEMENT.IMPLEMENT_ID))
                .where(IMPLEMENT.UUID.eq(implementUuid))
                .fetchOne(0, Integer.class));
    }

    @Test
    void solicitarDebeSeguirSiendoExitosaCuandoNoExistenCoordinadoresHumanosActivos() {
        assumeTrue(countActiveHumanCoordinators() == 0, "El entorno ya tiene coordinadores humanos activos");

        UUID requesterUuid = insertUser("docente", "Docente Sin Coordinadores");
        UUID roomUuid = insertRoom();
        UUID subjectUuid = insertSubject();
        UUID implementUuid = insertImplement(10);

        String expectedMessage = notificationMessage("Docente Sin Coordinadores");
        int notificationsBefore = notificationCount(notificationTitle(), expectedMessage);
        setAuthenticatedUser(requesterUuid, "DOCENTE");

        LoanSummaryView created = solicitarPrestamoUseCase.solicitar(new SolicitarPrestamoCommand(
                requesterUuid,
                roomUuid,
                subjectUuid,
                OffsetDateTime.parse("2026-06-24T09:30:00-04:00"),
                null,
                List.of(new SolicitarPrestamoItemCommand(implementUuid, 2))
        ));
        loanUuidsToCleanup.add(created.uuid());
        SecurityContextHolder.clearContext();

        assertNotNull(created);
        assertEquals(notificationsBefore, notificationCount(notificationTitle(), expectedMessage));
    }

    @Test
    void solicitarDebeSeguirAnotadoConTransactional() throws NoSuchMethodException {
        var method = SolicitarPrestamoUseCase.class.getMethod("solicitar", SolicitarPrestamoCommand.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
    }

    private UUID insertUser(String roleName, String displayName) {
        UUID userUuid = UUID.randomUUID();
        String suffix = userUuid.toString().substring(0, 8);
        Long roleId = dsl.select(ROLE.ID)
                .from(ROLE)
                .where(ROLE.NAME.eq(roleName))
                .fetchOne(ROLE.ID);

        dsl.insertInto(USER)
                .set(USER.UUID, userUuid)
                .set(USER.ROLE_ID, roleId)
                .set(USER.NAME, displayName)
                .set(USER.RUT, "rut-" + suffix)
                .set(USER.EMAIL, "it-" + suffix + "@panol.test")
                .set(USER.PASSWORD_HASH, "$2a$10$falsa_pero_valida_para_not_null")
                .set(USER.ACTIVE, true)
                .execute();

        userUuidsToCleanup.add(userUuid);
        return userUuid;
    }

    private UUID insertRoom() {
        UUID roomUuid = UUID.randomUUID();
        dsl.insertInto(ROOM)
                .set(ROOM.UUID, roomUuid)
                .set(ROOM.NAME, "Sala IT " + roomUuid.toString().substring(0, 8))
                .set(ROOM.DESCRIPTION, "Sala de prueba")
                .set(ROOM.ACTIVE, true)
                .execute();
        roomUuidsToCleanup.add(roomUuid);
        return roomUuid;
    }

    private UUID insertSubject() {
        UUID subjectUuid = UUID.randomUUID();
        String suffix = subjectUuid.toString().substring(0, 8).toUpperCase();
        dsl.insertInto(SUBJECT)
                .set(SUBJECT.UUID, subjectUuid)
                .set(SUBJECT.CODE, "IT-" + suffix)
                .set(SUBJECT.NAME, "Asignatura IT " + suffix)
                .set(SUBJECT.ACTIVE, true)
                .execute();
        subjectUuidsToCleanup.add(subjectUuid);
        return subjectUuid;
    }

    private UUID insertImplement(int availableQuantity) {
        UUID implementUuid = UUID.randomUUID();
        String suffix = implementUuid.toString().substring(0, 8);
        dsl.insertInto(IMPLEMENT)
                .set(IMPLEMENT.UUID, implementUuid)
                .set(IMPLEMENT.NAME, "Implemento IT " + suffix)
                .set(IMPLEMENT.DESCRIPTION, "Implemento de prueba")
                .set(IMPLEMENT.ITEM_TYPE, ItemTypeEnum.consumable)
                .set(IMPLEMENT.ACTIVE, true)
                .execute();
        Long implementId = findImplementId(implementUuid);
        dsl.insertInto(STOCK)
                .set(STOCK.IMPLEMENT_ID, implementId)
                .set(STOCK.TOTAL_STOCK, availableQuantity)
                .set(STOCK.MIN_STOCK, 0)
                .set(STOCK.AVAILABLE, availableQuantity)
                .set(STOCK.RESERVED, 0)
                .set(STOCK.LOANED, 0)
                .set(STOCK.DAMAGED, 0)
                .execute();
        implementUuidsToCleanup.add(implementUuid);
        return implementUuid;
    }

    private int countActiveHumanCoordinators() {
        return dsl.selectCount()
                .from(USER)
                .join(ROLE).on(ROLE.ID.eq(USER.ROLE_ID))
                .where(
                        ROLE.NAME.eq("coordinador")
                                .and(USER.ACTIVE.isTrue())
                                .and(USER.UUID.ne(SYSTEM_OUTBOX_USER_UUID))
                )
                .fetchOne(0, Integer.class);
    }

    private Long findUserId(UUID userUuid) {
        return dsl.select(USER.ID)
                .from(USER)
                .where(USER.UUID.eq(userUuid))
                .fetchOne(USER.ID);
    }

    private Long findImplementId(UUID implementUuid) {
        return dsl.select(IMPLEMENT.ID)
                .from(IMPLEMENT)
                .where(IMPLEMENT.UUID.eq(implementUuid))
                .fetchOne(IMPLEMENT.ID);
    }

    private int notificationCount(String title, String message) {
        return dsl.selectCount()
                .from(NOTIFICATION)
                .where(NOTIFICATION.TITLE.eq(title).and(NOTIFICATION.MESSAGE.eq(message)))
                .fetchOne(0, Integer.class);
    }

    private int userNotificationCount(Long userId, String title, String message) {
        return dsl.selectCount()
                .from(NOTIFICATION)
                .where(NOTIFICATION.USER_ID.eq(userId)
                        .and(NOTIFICATION.TITLE.eq(title))
                        .and(NOTIFICATION.MESSAGE.eq(message)))
                .fetchOne(0, Integer.class);
    }

    private void setAuthenticatedUser(UUID userUuid, String role) {
        Jwt jwt = Jwt.withTokenValue("integration-token")
                .header("alg", "none")
                .subject(userUuid.toString())
                .claim("role", role)
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }

    private String notificationTitle() {
        return "Nueva solicitud de pr\u00e9stamo";
    }

    private String notificationMessage(String requesterName) {
        return "El docente " + requesterName + " ha enviado una nueva solicitud";
    }

    private void cleanupLoans() {
        if (loanUuidsToCleanup.isEmpty()) {
            return;
        }
        dsl.deleteFrom(LOAN)
                .where(LOAN.UUID.in(loanUuidsToCleanup))
                .execute();
        loanUuidsToCleanup.clear();
    }

    private void cleanupUsers() {
        if (userUuidsToCleanup.isEmpty()) {
            return;
        }
        dsl.deleteFrom(USER)
                .where(USER.UUID.in(userUuidsToCleanup))
                .execute();
        userUuidsToCleanup.clear();
    }

    private void cleanupRooms() {
        if (roomUuidsToCleanup.isEmpty()) {
            return;
        }
        dsl.deleteFrom(ROOM)
                .where(ROOM.UUID.in(roomUuidsToCleanup))
                .execute();
        roomUuidsToCleanup.clear();
    }

    private void cleanupSubjects() {
        if (subjectUuidsToCleanup.isEmpty()) {
            return;
        }
        dsl.deleteFrom(SUBJECT)
                .where(SUBJECT.UUID.in(subjectUuidsToCleanup))
                .execute();
        subjectUuidsToCleanup.clear();
    }

    private void cleanupImplements() {
        if (implementUuidsToCleanup.isEmpty()) {
            return;
        }
        dsl.deleteFrom(IMPLEMENT)
                .where(IMPLEMENT.UUID.in(implementUuidsToCleanup))
                .execute();
        implementUuidsToCleanup.clear();
    }

    @TestConfiguration
    static class NoOpSchedulingConfig {

        @Bean(name = "taskScheduler")
        TaskScheduler taskScheduler() {
            return new NoOpTaskScheduler();
        }
    }

    private static final class NoOpTaskScheduler implements TaskScheduler {

        private static final ScheduledFuture<Object> NO_OP_FUTURE = new NoOpScheduledFuture();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            return NO_OP_FUTURE;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            return NO_OP_FUTURE;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            return NO_OP_FUTURE;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            return NO_OP_FUTURE;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            return NO_OP_FUTURE;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            return NO_OP_FUTURE;
        }
    }

    private static final class NoOpScheduledFuture implements ScheduledFuture<Object> {

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
