package com.panol_project.backendpanol.modules.loan.api;

import com.panol_project.backendpanol.modules.loan.api.dto.CancelLoanV2Request;
import com.panol_project.backendpanol.modules.loan.api.dto.CompleteLoanV2Request;
import com.panol_project.backendpanol.modules.loan.api.dto.CreateLoanV2Request;
import com.panol_project.backendpanol.modules.loan.api.dto.DeliverLoanV2Request;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanItemV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanPageV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanRoomV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanStateDatesV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanStatusTimelineEntryV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanSubjectV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.ReturnLoanV2Request;
import com.panol_project.backendpanol.modules.loan.api.dto.ReviewLoanV2Request;
import com.panol_project.backendpanol.modules.loan.application.GestionPrestamoUseCase;
import com.panol_project.backendpanol.modules.loan.application.SolicitarPrestamoUseCase;
import com.panol_project.backendpanol.modules.loan.application.dto.CancelarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.CompletarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.DevolverPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.DevolverPrestamoConsumableCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.DevolverPrestamoIndividualCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.EntregarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.EntregarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.RevisarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.RevisarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanStateDatesView;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatus;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryPage;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatusTimelineEntry;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/loans")
public class LoanV2Controller {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final SolicitarPrestamoUseCase solicitarPrestamoUseCase;
    private final GestionPrestamoUseCase gestionPrestamoUseCase;
    private final CurrentUserUuidResolver currentUserUuidResolver;

    public LoanV2Controller(
            SolicitarPrestamoUseCase solicitarPrestamoUseCase,
            GestionPrestamoUseCase gestionPrestamoUseCase,
            CurrentUserUuidResolver currentUserUuidResolver
    ) {
        this.solicitarPrestamoUseCase = solicitarPrestamoUseCase;
        this.gestionPrestamoUseCase = gestionPrestamoUseCase;
        this.currentUserUuidResolver = currentUserUuidResolver;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DOCENTE', 'COORDINADOR')")
    public LoanV2Response solicitarPrestamo(@Valid @RequestBody CreateLoanV2Request request, Authentication authentication) {
        UUID requesterUuid = currentUserUuidResolver.resolveCurrentUserUuid(authentication)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));

        List<SolicitarPrestamoItemCommand> items = request.items().stream()
                .map(item -> new SolicitarPrestamoItemCommand(item.implementUuid(), item.requestedQuantity()))
                .toList();

        LoanSummaryView created = solicitarPrestamoUseCase.solicitar(
                new SolicitarPrestamoCommand(
                        requesterUuid,
                        request.roomUuid(),
                        request.subjectUuid(),
                        request.scheduledAt(),
                        request.expectedReturnAt(),
                        request.notes(),
                        items
                )
        );

        return toResponse(created);
    }

    @PatchMapping("/{loanUuid}")
    @PreAuthorize("hasAnyRole('DOCENTE', 'COORDINADOR')")
    public LoanV2Response modificarPrestamo(
            @PathVariable UUID loanUuid,
            @Valid @RequestBody CreateLoanV2Request request,
            Authentication authentication
    ) {
        UUID requesterUuid = currentUserUuidResolver.resolveCurrentUserUuid(authentication)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));

        List<SolicitarPrestamoItemCommand> items = request.items().stream()
                .map(item -> new SolicitarPrestamoItemCommand(item.implementUuid(), item.requestedQuantity()))
                .toList();

        LoanSummaryView updated = solicitarPrestamoUseCase.modificar(
                loanUuid,
                new SolicitarPrestamoCommand(
                        requesterUuid,
                        request.roomUuid(),
                        request.subjectUuid(),
                        request.scheduledAt(),
                        request.expectedReturnAt(),
                        request.notes(),
                        items
                )
        );

        return toResponse(updated);
    }

    @GetMapping
    public LoanPageV2Response listarPrestamos(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "false") Boolean mine,
            Authentication authentication
    ) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        validatePagination(resolvedPage, resolvedSize);

        UUID currentUserUuid = resolveCurrentUserUuid(authentication);
        boolean isDocente = hasRole(authentication, "ROLE_DOCENTE");
        boolean onlyMine = isDocente || Boolean.TRUE.equals(mine);

        LoanSummaryPage summaryPage = gestionPrestamoUseCase.listar(
                onlyMine ? currentUserUuid : null,
                resolvedPage,
                resolvedSize
        );

        return toPageResponse(summaryPage);
    }

    @GetMapping("/{loanUuid}")
    public LoanV2Response obtenerPrestamo(
            @PathVariable UUID loanUuid,
            Authentication authentication
    ) {
        LoanSummaryView loan = findLoanVisibleForCurrentUser(authentication, loanUuid);
        return toResponse(loan);
    }

    @GetMapping("/{loanUuid}/state-dates")
    public LoanStateDatesV2Response obtenerFechasEstadoPrestamo(
            @PathVariable UUID loanUuid,
            Authentication authentication
    ) {
        findLoanVisibleForCurrentUser(authentication, loanUuid);
        return toStateDatesResponse(gestionPrestamoUseCase.obtenerFechasEstado(loanUuid));
    }

    @GetMapping("/{loanUuid}/status-timeline")
    public List<LoanStatusTimelineEntryV2Response> obtenerTimelineEstadoPrestamo(
            @PathVariable UUID loanUuid,
            Authentication authentication
    ) {
        findLoanVisibleForCurrentUser(authentication, loanUuid);
        return gestionPrestamoUseCase.obtenerTimelineEstado(loanUuid).stream()
                .map(this::toTimelineResponse)
                .toList();
    }

    @PatchMapping("/{loanUuid}/review")
    @PreAuthorize("hasRole('COORDINADOR')")
    public LoanV2Response revisarPrestamo(
            @PathVariable UUID loanUuid,
            @Valid @RequestBody ReviewLoanV2Request request,
            Authentication authentication
    ) {
        UUID actorUuid = resolveCurrentUserUuid(authentication);
        LoanSummaryView reviewed = gestionPrestamoUseCase.revisar(
                new RevisarPrestamoCommand(
                        loanUuid,
                        actorUuid,
                        request.decision(),
                        request.notes(),
                        request.items() == null
                                ? List.of()
                                : request.items().stream()
                                        .map(item -> new RevisarPrestamoItemCommand(
                                                item.implementUuid(),
                                                item.approvedQuantity()
                                        ))
                                        .toList()
                )
        );
        return toResponse(reviewed);
    }

    @PostMapping("/{loanUuid}/delivery")
    @PreAuthorize("hasRole('COORDINADOR')")
    public LoanV2Response entregarPrestamo(
            @PathVariable UUID loanUuid,
            @Valid @RequestBody DeliverLoanV2Request request,
            Authentication authentication
    ) {
        UUID actorUuid = resolveCurrentUserUuid(authentication);
        LoanSummaryView delivered = gestionPrestamoUseCase.entregar(
                new EntregarPrestamoCommand(
                        loanUuid,
                        actorUuid,
                        request.notes(),
                        request.items().stream().map(item -> new EntregarPrestamoItemCommand(
                                item.implementUuid(),
                                item.quantity(),
                                item.assetCodes()
                        )).toList()
                )
        );
        return toResponse(delivered);
    }

    @PostMapping("/{loanUuid}/complete")
    @PreAuthorize("hasRole('COORDINADOR')")
    public LoanV2Response completarPrestamo(
            @PathVariable UUID loanUuid,
            @Valid @RequestBody(required = false) CompleteLoanV2Request request,
            Authentication authentication
    ) {
        UUID actorUuid = resolveCurrentUserUuid(authentication);
        LoanSummaryView completed = gestionPrestamoUseCase.completar(
                new CompletarPrestamoCommand(
                        loanUuid,
                        actorUuid,
                        request == null ? null : request.notes()
                )
        );
        return toResponse(completed);
    }

    @PatchMapping("/{loanUuid}/cancel")
    @PreAuthorize("hasAnyRole('DOCENTE', 'COORDINADOR')")
    public LoanV2Response cancelarPrestamo(
            @PathVariable UUID loanUuid,
            @RequestBody(required = false) CancelLoanV2Request request,
            Authentication authentication
    ) {
        requireOwnedPendingLoanForRequesterActions(authentication, loanUuid, "LOAN_CANCEL_FORBIDDEN", "LOAN_CANCEL_INVALID_STATE");
        UUID actorUuid = resolveCurrentUserUuid(authentication);

        LoanSummaryView cancelled = gestionPrestamoUseCase.cancelar(
                new CancelarPrestamoCommand(
                        loanUuid,
                        actorUuid,
                        request == null ? null : request.notes()
                )
        );
        return toResponse(cancelled);
    }

    @PostMapping("/{loanUuid}/return")
    @PreAuthorize("hasRole('COORDINADOR')")
    public LoanV2Response devolverPrestamo(
            @PathVariable UUID loanUuid,
            @Valid @RequestBody ReturnLoanV2Request request,
            Authentication authentication
    ) {
        UUID actorUuid = resolveCurrentUserUuid(authentication);
        LoanSummaryView completed = gestionPrestamoUseCase.devolver(
                new DevolverPrestamoCommand(
                        loanUuid,
                        actorUuid,
                        request.notes(),
                        request.returnedIndividuals() == null
                                ? List.of()
                                : request.returnedIndividuals().stream()
                                        .map(item -> new DevolverPrestamoIndividualCommand(
                                                item.individualUuid(),
                                                item.returnCondition()
                                        )).toList(),
                        request.consumableReturns() == null
                                ? List.of()
                                : request.consumableReturns().stream()
                                        .map(item -> new DevolverPrestamoConsumableCommand(
                                                item.implementUuid(),
                                                item.quantity()
                                        )).toList()
                )
        );
        return toResponse(completed);
    }

    private LoanV2Response toResponse(LoanSummaryView loan) {
        return new LoanV2Response(
                loan.uuid(),
                loan.requesterUuid(),
                loan.status().literal(),
                loan.scheduledAt(),
                loan.expectedReturnAt(),
                loan.createdAt(),
                loan.completedAt(),
                loan.room() == null ? null : new LoanRoomV2Response(loan.room().uuid(), loan.room().name()),
                loan.subject() == null ? null : new LoanSubjectV2Response(loan.subject().uuid(), loan.subject().name()),
                loan.items().stream()
                        .map(item -> new LoanItemV2Response(
                                item.implementUuid(),
                                item.implementName(),
                                item.requestedQuantity(),
                                item.reservedQuantity(),
                                item.deliveredQuantity()
                        ))
                        .toList()
        );
    }

    private LoanStateDatesV2Response toStateDatesResponse(LoanStateDatesView dates) {
        return new LoanStateDatesV2Response(
                dates.approvedAt(),
                dates.preparedAt(),
                dates.deliveredAt(),
                dates.completedAt(),
                dates.rejectedAt(),
                dates.cancelledAt(),
                dates.expiredAt(),
                dates.overdueAt()
        );
    }

    private LoanStatusTimelineEntryV2Response toTimelineResponse(LoanStatusTimelineEntry entry) {
        return new LoanStatusTimelineEntryV2Response(
                entry.historyId(),
                entry.fromStatus() == null ? null : entry.fromStatus().literal(),
                entry.toStatus() == null ? null : entry.toStatus().literal(),
                entry.actorUserId(),
                entry.actorName(),
                entry.actorEmail(),
                entry.notes(),
                entry.changedAt()
        );
    }

    private LoanPageV2Response toPageResponse(LoanSummaryPage page) {
        List<LoanV2Response> items = page.items().stream().map(this::toResponse).toList();
        boolean hasNext = page.page() < page.totalPages();
        boolean hasPrevious = page.page() > 1;
        return new LoanPageV2Response(
                items,
                page.page(),
                page.size(),
                page.totalItems(),
                page.totalPages(),
                hasNext,
                hasPrevious
        );
    }

    private void validatePagination(int page, int size) {
        if (page < 1) {
            throw new BadRequestException("LOAN_PAGE_INVALID", "page debe ser mayor o igual a 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("LOAN_SIZE_INVALID", "size debe estar entre 1 y " + MAX_SIZE);
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private LoanSummaryView findLoanVisibleForCurrentUser(Authentication authentication, UUID loanUuid) {
        UUID currentUserUuid = resolveCurrentUserUuid(authentication);
        boolean isDocente = hasRole(authentication, "ROLE_DOCENTE");

        LoanSummaryView loan = gestionPrestamoUseCase.obtenerDetalle(loanUuid);
        if (isDocente && !currentUserUuid.equals(loan.requesterUuid())) {
            throw new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado");
        }
        return loan;
    }

    private LoanSummaryView requireOwnedPendingLoanForRequesterActions(
            Authentication authentication,
            UUID loanUuid,
            String forbiddenCode,
            String invalidStateCode
    ) {
        LoanSummaryView loan = findLoanVisibleForCurrentUser(authentication, loanUuid);
        UUID currentUserUuid = resolveCurrentUserUuid(authentication);
        if (!currentUserUuid.equals(loan.requesterUuid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, forbiddenCode, "No tienes permisos para gestionar este prestamo");
        }
        if (loan.status() != LoanStatus.PENDING) {
            throw new BadRequestException(invalidStateCode, "Solo puedes gestionar prestamos en estado pending");
        }
        return loan;
    }

    private UUID resolveCurrentUserUuid(Authentication authentication) {
        return currentUserUuidResolver.resolveCurrentUserUuid(authentication)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));
    }
}



