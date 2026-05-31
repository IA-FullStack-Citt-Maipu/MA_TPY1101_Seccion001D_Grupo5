package com.panol_project.backendpanol.modules.loan.application;

import com.panol_project.backendpanol.modules.loan.application.dto.CancelarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.CompletarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.DevolverPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.EntregarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.RevisarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanCancelCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryResult;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnConsumableItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnIndividual;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnResult;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewDecision;
import com.panol_project.backendpanol.modules.loan.domain.LoanStateDatesView;
import com.panol_project.backendpanol.modules.loan.domain.LoanStatusTimelineEntry;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryView;
import com.panol_project.backendpanol.modules.loan.domain.LoanSummaryPage;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.error.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GestionPrestamoUseCase {

    private final LoanRepositoryPort loanRepositoryPort;

    public GestionPrestamoUseCase(LoanRepositoryPort loanRepositoryPort) {
        this.loanRepositoryPort = loanRepositoryPort;
    }

    @Transactional(readOnly = true)
    public List<LoanSummaryView> listar() {
        return loanRepositoryPort.findAllVisibleLoanSummaries();
    }

    @Transactional(readOnly = true)
    public LoanSummaryPage listar(UUID requesterUuid, int page, int size) {
        return loanRepositoryPort.findVisibleLoanSummaries(requesterUuid, page, size);
    }

    @Transactional(readOnly = true)
    public LoanSummaryView obtenerDetalle(UUID loanUuid) {
        return findVisibleLoanSummaryOrThrow(loanUuid);
    }

    @Transactional
    public LoanSummaryView revisar(RevisarPrestamoCommand command) {
        LoanReviewDecision decision = LoanReviewDecision.fromLiteral(command.decision())
                .orElseThrow(() -> new BadRequestException("LOAN_REVIEW_DECISION_INVALID", "decision debe ser APPROVE o REJECT"));

        String notes = normalizeOptionalText(command.notes());

        if (decision == LoanReviewDecision.REJECT) {
            if (notes == null) {
                throw new BadRequestException("LOAN_REJECTION_NOTES_REQUIRED", "notes es obligatorio al rechazar");
            }
        }

        LoanAggregate updated = loanRepositoryPort.reviewLoan(
                new LoanReviewCommand(
                        command.loanUuid(),
                        command.actorUuid(),
                        decision,
                        notes
                )
        );

        return findVisibleLoanSummaryOrThrow(updated.uuid());
    }

    @Transactional
    public LoanSummaryView cancelar(CancelarPrestamoCommand command) {
        LoanAggregate cancelled = loanRepositoryPort.cancelLoan(
                new LoanCancelCommand(
                        command.loanUuid(),
                        command.actorUuid(),
                        normalizeOptionalText(command.notes())
                )
        );
        return findVisibleLoanSummaryOrThrow(cancelled.uuid());
    }

    @Transactional
    public LoanSummaryView entregar(EntregarPrestamoCommand command) {
        LoanDeliveryResult delivery = loanRepositoryPort.deliverLoan(
                new LoanDeliveryCommand(
                        command.loanUuid(),
                        command.actorUuid(),
                        command.items().stream().map(item -> new LoanDeliveryItem(
                                item.implementUuid(),
                                item.quantity(),
                                item.assetCodes()
                        )).toList()
                )
        );

        return findVisibleLoanSummaryOrThrow(delivery.loan().uuid());
    }

    @Transactional
    public LoanSummaryView devolver(DevolverPrestamoCommand command) {
        boolean hasIndividuals = command.returnedIndividuals() != null && !command.returnedIndividuals().isEmpty();
        boolean hasConsumable = command.consumableReturns() != null && !command.consumableReturns().isEmpty();
        if (!hasIndividuals && !hasConsumable) {
            throw new BadRequestException("LOAN_RETURN_EMPTY", "Debes incluir al menos un retorno (individual o consumable/reusable)");
        }

        LoanReturnResult returned = loanRepositoryPort.returnLoan(
                new LoanReturnCommand(
                        command.loanUuid(),
                        command.actorUuid(),
                        command.returnedIndividuals() == null
                                ? List.of()
                                : command.returnedIndividuals().stream()
                                        .map(item -> new LoanReturnIndividual(item.individualUuid(), item.returnCondition()))
                                        .toList(),
                        command.consumableReturns() == null
                                ? List.of()
                                : command.consumableReturns().stream()
                                        .map(item -> new LoanReturnConsumableItem(item.implementUuid(), item.quantity()))
                                        .toList()
                )
        );

        return findVisibleLoanSummaryOrThrow(returned.loan().uuid());
    }

    @Transactional
    public LoanSummaryView completar(CompletarPrestamoCommand command) {
        LoanReturnResult completed = loanRepositoryPort.completeLoan(
                new com.panol_project.backendpanol.modules.loan.domain.LoanCompleteCommand(
                        command.loanUuid(),
                        command.actorUuid()
                )
        );

        return findVisibleLoanSummaryOrThrow(completed.loan().uuid());
    }

    @Transactional(readOnly = true)
    public LoanStateDatesView obtenerFechasEstado(UUID loanUuid) {
        return loanRepositoryPort.findLoanStateDatesByUuid(loanUuid)
                .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<LoanStatusTimelineEntry> obtenerTimelineEstado(UUID loanUuid) {
        findVisibleLoanSummaryOrThrow(loanUuid);
        return loanRepositoryPort.findLoanStatusTimelineByUuid(loanUuid);
    }

    @Transactional
    public int marcarPrestamosOverdue(UUID actorUuid, OffsetDateTime currentTime) {
        return loanRepositoryPort.markOverdueLoans(actorUuid, currentTime);
    }

    @Transactional
    public int expirarPrestamosPendientes(UUID actorUuid, OffsetDateTime currentTime, int graceMinutes) {
        return loanRepositoryPort.expirePendingLoans(actorUuid, currentTime, graceMinutes);
    }

    private String normalizeOptionalText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private LoanSummaryView findVisibleLoanSummaryOrThrow(java.util.UUID loanUuid) {
        return loanRepositoryPort.findVisibleLoanSummaryByUuid(loanUuid)
                .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Prestamo no encontrado"));
    }
}
