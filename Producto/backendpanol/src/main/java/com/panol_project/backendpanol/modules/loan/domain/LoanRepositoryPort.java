package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface LoanRepositoryPort {

    boolean existsActiveRequesterByUuid(UUID requesterUuid);

    boolean existsActiveRoomByUuid(UUID roomUuid);

    boolean existsActiveSubjectByUuid(UUID subjectUuid);

    Optional<LoanImplementAvailability> findImplementAvailabilityByUuid(UUID implementUuid);

    List<LoanRequestedItemAvailability> findRequestedItemAvailabilities(
            List<UUID> implementUuids,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            UUID excludeLoanUuid
    );

    boolean existsPendingLoanConflict(
            UUID requesterUuid,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            List<UUID> implementUuids
    );

    boolean existsPendingLoanConflict(
            UUID requesterUuid,
            UUID excludeLoanUuid,
            OffsetDateTime scheduledAt,
            OffsetDateTime expectedReturnAt,
            List<UUID> implementUuids
    );

    LoanAggregate createPendingLoan(LoanCreateCommand command);

    LoanAggregate updatePendingLoan(LoanUpdateCommand command);

    Optional<LoanSummaryView> findVisibleLoanSummaryByUuid(UUID loanUuid);

    LoanSummaryPage findVisibleLoanSummaries(
            UUID requesterUuid,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    );

    List<LoanSummaryView> findAllVisibleLoanSummaries();

    LoanAggregate reviewLoan(LoanReviewCommand command);

    LoanAggregate prepareLoan(LoanPrepareCommand command);

    LoanAggregate cancelLoan(LoanCancelCommand command);

    LoanDeliveryResult deliverLoan(LoanDeliveryCommand command);

    LoanReturnResult completeLoan(LoanCompleteCommand command);

    LoanReturnResult returnLoan(LoanReturnCommand command);

    Optional<LoanReturnContextView> findLoanReturnContextByUuid(UUID loanUuid);

    Optional<LoanStateDatesView> findLoanStateDatesByUuid(UUID loanUuid);

    List<LoanStatusTimelineEntry> findLoanStatusTimelineByUuid(UUID loanUuid);

    int markOverdueLoans(UUID actorUuid, OffsetDateTime currentTime);

    int expirePendingLoans(UUID actorUuid, OffsetDateTime currentTime, int graceMinutes);
}
