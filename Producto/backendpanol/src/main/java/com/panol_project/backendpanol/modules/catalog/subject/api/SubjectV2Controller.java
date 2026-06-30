package com.panol_project.backendpanol.modules.catalog.subject.api;

import com.panol_project.backendpanol.modules.catalog.subject.api.dto.SubjectSelectorV2Response;
import com.panol_project.backendpanol.modules.catalog.subject.application.SubjectService;
import com.panol_project.backendpanol.modules.catalog.subject.domain.SubjectOption;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/subjects")
public class SubjectV2Controller {

    private final SubjectService subjectService;

    public SubjectV2Controller(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SubjectSelectorV2Response> listSelector() {
        return subjectService.listarSelector().stream()
                .map(this::toResponse)
                .toList();
    }

    private SubjectSelectorV2Response toResponse(SubjectOption subject) {
        return new SubjectSelectorV2Response(subject.id(), subject.uuid(), subject.code(), subject.name());
    }
}
