package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepositoryPort {

    boolean existsActiveRequesterByUuid(UUID requesterUuid);

    boolean existsActiveRoomByUuid(UUID roomUuid);

    boolean existsActiveSubjectByUuid(UUID subjectUuid);

    Optional<LoanImplementAvailability> findImplementAvailabilityByUuid(UUID implementUuid);

    boolean existsPendingLoanConflict(UUID requesterUuid, List<UUID> implementUuids);

    boolean existsPendingLoanConflict(UUID requesterUuid, UUID excludeLoanUuid, List<UUID> implementUuids);

    LoanAggregate createPendingLoan(LoanCreateCommand command);

    LoanAggregate updatePendingLoan(LoanUpdateCommand command);

    Optional<LoanSummaryView> findVisibleLoanSummaryByUuid(UUID loanUuid);

    LoanSummaryPage findVisibleLoanSummaries(UUID requesterUuid, int page, int size);

    List<LoanSummaryView> findAllVisibleLoanSummaries();

    LoanAggregate reviewLoan(LoanReviewCommand command);

    LoanDeliveryResult deliverLoan(LoanDeliveryCommand command);

    LoanReturnResult returnLoan(LoanReturnCommand command);
}
