package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;

public record LoanRequesterHistoryPage(
        LoanRequesterSummary requester,
        List<LoanRequesterHistoryItem> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
