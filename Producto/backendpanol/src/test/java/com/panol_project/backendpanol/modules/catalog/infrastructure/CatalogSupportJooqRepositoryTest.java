package com.panol_project.backendpanol.modules.catalog.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Room.ROOM;
import static com.panol_project.backendpanol.jooq.tables.Subject.SUBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.panol_project.backendpanol.modules.catalog.room.domain.RoomOption;
import com.panol_project.backendpanol.modules.catalog.room.infrastructure.RoomJooqRepository;
import com.panol_project.backendpanol.modules.catalog.subject.domain.SubjectOption;
import com.panol_project.backendpanol.modules.catalog.subject.infrastructure.SubjectJooqRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.outbox.worker-delay-ms=600000",
        "app.outbox.metrics-delay-ms=600000"
})
@ActiveProfiles("supabase")
class CatalogSupportJooqRepositoryTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private RoomJooqRepository roomJooqRepository;

    @Autowired
    private SubjectJooqRepository subjectJooqRepository;

    private final List<UUID> roomUuidsToCleanup = new ArrayList<>();
    private final List<UUID> subjectUuidsToCleanup = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cleanupSubjects();
        cleanupRooms();
    }

    @Test
    void findAllActiveRoomsDebeRetornarSoloActivasYOrdenadasPorNombre() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID inactiveRoomUuid = insertRoom("ZZ Sala Inactiva " + suffix, false);
        UUID activeRoomAUuid = insertRoom("QA Sala A " + suffix, true);
        UUID activeRoomZUuid = insertRoom("QA Sala Z " + suffix, true);

        List<RoomOption> rooms = roomJooqRepository.findAllActive();

        assertFalse(containsRoom(rooms, inactiveRoomUuid));
        assertTrue(containsRoom(rooms, activeRoomAUuid));
        assertTrue(containsRoom(rooms, activeRoomZUuid));
        assertTrue(indexOfRoom(rooms, activeRoomAUuid) < indexOfRoom(rooms, activeRoomZUuid));

        RoomOption roomA = findRoom(rooms, activeRoomAUuid);
        assertNotNull(roomA.id());
        assertEquals("QA Sala A " + suffix, roomA.name());
    }

    @Test
    void findAllActiveSubjectsDebeRetornarSoloActivasYMapearCodeOrdenadasPorNombre() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID inactiveSubjectUuid = insertSubject("ZZZ-" + suffix, "ZZ Asignatura Inactiva " + suffix, false);
        UUID activeSubjectAUuid = insertSubject("AAA-" + suffix, "QA Asignatura A " + suffix, true);
        UUID activeSubjectZUuid = insertSubject("ZZA-" + suffix, "QA Asignatura Z " + suffix, true);

        List<SubjectOption> subjects = subjectJooqRepository.findAllActive();

        assertFalse(containsSubject(subjects, inactiveSubjectUuid));
        assertTrue(containsSubject(subjects, activeSubjectAUuid));
        assertTrue(containsSubject(subjects, activeSubjectZUuid));
        assertTrue(indexOfSubject(subjects, activeSubjectAUuid) < indexOfSubject(subjects, activeSubjectZUuid));

        SubjectOption subjectA = findSubject(subjects, activeSubjectAUuid);
        assertNotNull(subjectA.id());
        assertEquals("AAA-" + suffix, subjectA.code());
        assertEquals("QA Asignatura A " + suffix, subjectA.name());
    }

    private UUID insertRoom(String name, boolean active) {
        UUID roomUuid = UUID.randomUUID();
        dsl.insertInto(ROOM)
                .set(ROOM.UUID, roomUuid)
                .set(ROOM.NAME, name)
                .set(ROOM.DESCRIPTION, "Sala de prueba")
                .set(ROOM.ACTIVE, active)
                .execute();
        roomUuidsToCleanup.add(roomUuid);
        return roomUuid;
    }

    private UUID insertSubject(String code, String name, boolean active) {
        UUID subjectUuid = UUID.randomUUID();
        dsl.insertInto(SUBJECT)
                .set(SUBJECT.UUID, subjectUuid)
                .set(SUBJECT.CODE, code)
                .set(SUBJECT.NAME, name)
                .set(SUBJECT.ACTIVE, active)
                .execute();
        subjectUuidsToCleanup.add(subjectUuid);
        return subjectUuid;
    }

    private boolean containsRoom(List<RoomOption> rooms, UUID roomUuid) {
        return rooms.stream().anyMatch(room -> room.uuid().equals(roomUuid));
    }

    private RoomOption findRoom(List<RoomOption> rooms, UUID roomUuid) {
        return rooms.stream()
                .filter(room -> room.uuid().equals(roomUuid))
                .findFirst()
                .orElseThrow();
    }

    private int indexOfRoom(List<RoomOption> rooms, UUID roomUuid) {
        for (int index = 0; index < rooms.size(); index++) {
            if (rooms.get(index).uuid().equals(roomUuid)) {
                return index;
            }
        }
        return -1;
    }

    private boolean containsSubject(List<SubjectOption> subjects, UUID subjectUuid) {
        return subjects.stream().anyMatch(subject -> subject.uuid().equals(subjectUuid));
    }

    private SubjectOption findSubject(List<SubjectOption> subjects, UUID subjectUuid) {
        return subjects.stream()
                .filter(subject -> subject.uuid().equals(subjectUuid))
                .findFirst()
                .orElseThrow();
    }

    private int indexOfSubject(List<SubjectOption> subjects, UUID subjectUuid) {
        for (int index = 0; index < subjects.size(); index++) {
            if (subjects.get(index).uuid().equals(subjectUuid)) {
                return index;
            }
        }
        return -1;
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

    @TestConfiguration
    static class NoOpSchedulingConfig {

        @Bean(name = "taskScheduler")
        TaskScheduler taskScheduler() {
            return new NoOpTaskScheduler();
        }
    }

    private static final class NoOpTaskScheduler implements TaskScheduler {

        @Override
        public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, java.time.Instant startTime) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Instant startTime, java.time.Duration period) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Duration period) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Instant startTime, java.time.Duration delay) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Duration delay) {
            return null;
        }
    }
}
