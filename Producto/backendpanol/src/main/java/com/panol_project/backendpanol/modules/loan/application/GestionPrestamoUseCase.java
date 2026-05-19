package com.panol_project.backendpanol.modules.loan.application;

import com.panol_project.backendpanol.modules.catalog.stock.application.contract.StockMovementContract;
import com.panol_project.backendpanol.modules.loan.application.dto.DevolverPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.EntregarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.application.dto.RevisarPrestamoCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanAggregate;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanDeliveryResult;
import com.panol_project.backendpanol.modules.loan.domain.LoanRepositoryPort;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnFungibleItem;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnIndividual;
import com.panol_project.backendpanol.modules.loan.domain.LoanReturnResult;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewCommand;
import com.panol_project.backendpanol.modules.loan.domain.LoanReviewDecision;
import com.panol_project.backendpanol.modules.loan.domain.LoanStockMovement;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import com.panol_project.backendpanol.shared.outbox.application.OutboxService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GestionPrestamoUseCase {

    private final LoanRepositoryPort loanRepositoryPort;
    private final StockMovementContract stockMovementContract;
    private final OutboxService outboxService;

    public GestionPrestamoUseCase(
            LoanRepositoryPort loanRepositoryPort,
            StockMovementContract stockMovementContract,
            OutboxService outboxService
    ) {
        this.loanRepositoryPort = loanRepositoryPort;
        this.stockMovementContract = stockMovementContract;
        this.outboxService = outboxService;
    }

    @Transactional(readOnly = true)
    public List<LoanAggregate> listar() {
        return loanRepositoryPort.findAllVisibleLoans();
    }

    @Transactional
    public LoanAggregate revisar(RevisarPrestamoCommand command) {
        LoanReviewDecision decision = LoanReviewDecision.fromLiteral(command.decision())
                .orElseThrow(() -> new BadRequestException("LOAN_REVIEW_DECISION_INVALID", "decision debe ser APPROVE o REJECT"));

        if (decision == LoanReviewDecision.REJECT) {
            String rejectionReason = normalizeOptionalText(command.rejectionReason());
            if (rejectionReason == null) {
                throw new BadRequestException("LOAN_REJECTION_REASON_REQUIRED", "rejection_reason es obligatorio al rechazar");
            }
        }

        LoanAggregate updated = loanRepositoryPort.reviewLoan(
                new LoanReviewCommand(
                        command.loanUuid(),
                        command.actorUuid(),
                        decision,
                        normalizeOptionalText(command.reviewNotes()),
                        normalizeOptionalText(command.rejectionReason())
                )
        );

        outboxService.enqueue(
                "loan",
                updated.uuid(),
                "LoanReviewed",
                command.actorUuid(),
                java.util.Map.of(
                        "loan_uuid", updated.uuid().toString(),
                        "decision", decision.name(),
                        "status", updated.status().literal()
                )
        );

        return updated;
    }

    @Transactional
    public LoanAggregate entregar(EntregarPrestamoCommand command) {
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

        applyStockMovements(delivery.stockMovements());

        outboxService.enqueue(
                "loan",
                delivery.loan().uuid(),
                "LoanDelivered",
                command.actorUuid(),
                java.util.Map.of(
                        "loan_uuid", delivery.loan().uuid().toString(),
                        "status", delivery.loan().status().literal()
                )
        );

        return delivery.loan();
    }

    @Transactional
    public LoanAggregate devolver(DevolverPrestamoCommand command) {
        boolean hasIndividuals = command.returnedIndividuals() != null && !command.returnedIndividuals().isEmpty();
        boolean hasFungible = command.fungibleReturns() != null && !command.fungibleReturns().isEmpty();
        if (!hasIndividuals && !hasFungible) {
            throw new BadRequestException("LOAN_RETURN_EMPTY", "Debes incluir al menos un retorno (individual o fungible)");
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
                        command.fungibleReturns() == null
                                ? List.of()
                                : command.fungibleReturns().stream()
                                        .map(item -> new LoanReturnFungibleItem(item.implementUuid(), item.quantity()))
                                        .toList()
                )
        );

        applyStockMovements(returned.stockMovements());

        outboxService.enqueue(
                "loan",
                returned.loan().uuid(),
                "LoanCompleted",
                command.actorUuid(),
                java.util.Map.of(
                        "loan_uuid", returned.loan().uuid().toString(),
                        "status", returned.loan().status().literal()
                )
        );

        return returned.loan();
    }

    private void applyStockMovements(List<LoanStockMovement> stockMovements) {
        for (LoanStockMovement movement : stockMovements) {
            stockMovementContract.applyMovement(
                    movement.implementUuid(),
                    movement.movementType(),
                    movement.quantity(),
                    movement.individualUuids(),
                    movement.condition()
            );
        }
    }

    private String normalizeOptionalText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
