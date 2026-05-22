package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepositoryPort {

    boolean existsActiveRequesterByUuid(UUID requesterUuid);

    boolean existsActiveRoomByUuid(UUID roomUuid);

    boolean existsActiveSubjectByUuid(UUID subjectUuid);

    boolean existsActiveImplementByUuid(UUID implementUuid);

    LoanAggregate createPendingLoan(LoanCreateCommand command);

    Optional<LoanSummaryView> findVisibleLoanSummaryByUuid(UUID loanUuid);

    List<LoanSummaryView> findAllVisibleLoanSummaries();

    LoanAggregate reviewLoan(LoanReviewCommand command);

    LoanDeliveryResult deliverLoan(LoanDeliveryCommand command);

    LoanReturnResult returnLoan(LoanReturnCommand command);
}
