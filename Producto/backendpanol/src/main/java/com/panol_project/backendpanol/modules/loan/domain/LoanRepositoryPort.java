package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.UUID;

public interface LoanRepositoryPort {

    boolean existsActiveRequesterByUuid(UUID requesterUuid);

    boolean existsActiveRoomByUuid(UUID roomUuid);

    boolean existsActiveSubjectByUuid(UUID subjectUuid);

    boolean existsActiveImplementByUuid(UUID implementUuid);

    LoanAggregate createPendingLoan(LoanCreateCommand command);

    List<LoanAggregate> findAllVisibleLoans();

    LoanAggregate reviewLoan(LoanReviewCommand command);

    LoanDeliveryResult deliverLoan(LoanDeliveryCommand command);

    LoanReturnResult returnLoan(LoanReturnCommand command);
}
