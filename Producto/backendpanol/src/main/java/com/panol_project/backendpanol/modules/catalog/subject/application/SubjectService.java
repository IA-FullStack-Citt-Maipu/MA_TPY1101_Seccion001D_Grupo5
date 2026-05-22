package com.panol_project.backendpanol.modules.catalog.subject.application;

import com.panol_project.backendpanol.modules.catalog.subject.domain.SubjectOption;
import com.panol_project.backendpanol.modules.catalog.subject.domain.SubjectRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectService {

    private final SubjectRepository repository;

    public SubjectService(SubjectRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SubjectOption> listarSelector() {
        return repository.findAllActive();
    }
}
