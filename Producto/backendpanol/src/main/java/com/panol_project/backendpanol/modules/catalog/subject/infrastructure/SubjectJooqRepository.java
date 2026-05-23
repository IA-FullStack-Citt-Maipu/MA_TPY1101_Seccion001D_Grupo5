package com.panol_project.backendpanol.modules.catalog.subject.infrastructure;

import static com.panol_project.backendpanol.jooq.tables.Subject.SUBJECT;

import com.panol_project.backendpanol.modules.catalog.subject.domain.SubjectOption;
import com.panol_project.backendpanol.modules.catalog.subject.domain.SubjectRepository;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class SubjectJooqRepository implements SubjectRepository {

    private final DSLContext dsl;

    public SubjectJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<SubjectOption> findAllActive() {
        return dsl.select(SUBJECT.ID, SUBJECT.UUID, SUBJECT.CODE, SUBJECT.NAME)
                .from(SUBJECT)
                .where(SUBJECT.ACTIVE.isTrue())
                .orderBy(SUBJECT.NAME.asc())
                .fetch(record -> new SubjectOption(
                        record.get(SUBJECT.ID),
                        record.get(SUBJECT.UUID),
                        record.get(SUBJECT.CODE),
                        record.get(SUBJECT.NAME)
                ));
    }
}
