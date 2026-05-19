package com.panol_project.backendpanol.modules.loan.api;

import com.panol_project.backendpanol.modules.loan.api.dto.CreateLoanV2Request;
import com.panol_project.backendpanol.modules.loan.api.dto.DeliverLoanV2Request;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanItemV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.LoanV2Response;
import com.panol_project.backendpanol.modules.loan.api.dto.ReturnLoanV2Request;
import com.panol_project.backendpanol.modules.loan.api.dto.ReviewLoanV2Request;
import com.panol_project.backendpanol.modules.loan.application.GestionPrestamoUseCase;
import com.panol_project.backendpanol.modules.loan.application.SolicitarPrestamoUseCase;
import com.panol_project.backendpanol.modules.loan.application.dto.DevolverPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.DevolverPrestamoFungibleCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.DevolverPrestamoIndividualCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.EntregarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.EntregarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.RevisarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.SolicitarPrestamoItemCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.shared.error.ApiException;
import com.panol_project.backendpanol.shared.security.CurrentUserUuidResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/loans")
public class LoanV2Controller {

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
    LoanV2Response solicitarPrestamo(@Valid @RequestBody CreateLoanV2Request request, Authentication authentication) {
        UUID requesterUuid = currentUserUuidResolver.resolveCurrentUserUuid(authentication)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));

        List<SolicitarPrestamoItemCommand> items = request.items().stream()
                .map(item -> new SolicitarPrestamoItemCommand(item.implementUuid(), item.requestedQuantity()))
                .toList();

        LoanAggregate created = solicitarPrestamoUseCase.solicitar(
                new SolicitarPrestamoCommand(
                        requesterUuid,
                        request.roomUuid(),
                        request.subjectUuid(),
                        request.scheduledAt(),
                        request.dueDate(),
                        items
                )
        );

        return toResponse(created);
    }

    @GetMapping
    List<LoanV2Response> listarPrestamos() {
        return gestionPrestamoUseCase.listar().stream()
                .map(this::toResponse)
                .toList();
    }

    @PatchMapping("/{loanUuid}/review")
    LoanV2Response revisarPrestamo(
            @PathVariable UUID loanUuid,
            @Valid @RequestBody ReviewLoanV2Request request,
            Authentication authentication
    ) {
        UUID actorUuid = resolveCurrentUserUuid(authentication);
        LoanAggregate reviewed = gestionPrestamoUseCase.revisar(
                new RevisarPrestamoCommand(
                        loanUuid,
                        actorUuid,
                        request.decision(),
                        request.reviewNotes(),
                        request.rejectionReason()
                )
        );
        return toResponse(reviewed);
    }

    @PostMapping("/{loanUuid}/delivery")
    LoanV2Response entregarPrestamo(
            @PathVariable UUID loanUuid,
            @Valid @RequestBody DeliverLoanV2Request request,
            Authentication authentication
    ) {
        UUID actorUuid = resolveCurrentUserUuid(authentication);
        LoanAggregate delivered = gestionPrestamoUseCase.entregar(
                new EntregarPrestamoCommand(
                        loanUuid,
                        actorUuid,
                        request.items().stream().map(item -> new EntregarPrestamoItemCommand(
                                item.implementUuid(),
                                item.quantity(),
                                item.assetCodes()
                        )).toList()
                )
        );
        return toResponse(delivered);
    }

    @PostMapping("/{loanUuid}/return")
    LoanV2Response devolverPrestamo(
            @PathVariable UUID loanUuid,
            @Valid @RequestBody ReturnLoanV2Request request,
            Authentication authentication
    ) {
        UUID actorUuid = resolveCurrentUserUuid(authentication);
        LoanAggregate completed = gestionPrestamoUseCase.devolver(
                new DevolverPrestamoCommand(
                        loanUuid,
                        actorUuid,
                        request.returnedIndividuals() == null
                                ? List.of()
                                : request.returnedIndividuals().stream()
                                        .map(item -> new DevolverPrestamoIndividualCommand(
                                                item.individualUuid(),
                                                item.returnCondition()
                                        )).toList(),
                        request.fungibleReturns() == null
                                ? List.of()
                                : request.fungibleReturns().stream()
                                        .map(item -> new DevolverPrestamoFungibleCommand(
                                                item.implementUuid(),
                                                item.quantity()
                                        )).toList()
                )
        );
        return toResponse(completed);
    }

    private LoanV2Response toResponse(LoanAggregate loan) {
        return new LoanV2Response(
                loan.uuid(),
                loan.requesterUuid(),
                loan.roomUuid(),
                loan.subjectUuid(),
                loan.status().literal(),
                loan.scheduledAt(),
                loan.dueDate(),
                loan.createdAt(),
                loan.items().stream().map(item -> new LoanItemV2Response(
                        item.implementUuid(),
                        item.requestedQuantity(),
                        item.reservedQuantity(),
                        item.deliveredQuantity()
                )).toList()
        );
    }

    private UUID resolveCurrentUserUuid(Authentication authentication) {
        return currentUserUuidResolver.resolveCurrentUserUuid(authentication)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticacion requerida"));
    }
}
