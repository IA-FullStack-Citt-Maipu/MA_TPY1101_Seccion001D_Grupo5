package com.panol_project.backendpanol.modules.catalog.subject.domain;

import java.util.List;

public interface SubjectRepository {

    List<SubjectOption> findAllActive();
}
