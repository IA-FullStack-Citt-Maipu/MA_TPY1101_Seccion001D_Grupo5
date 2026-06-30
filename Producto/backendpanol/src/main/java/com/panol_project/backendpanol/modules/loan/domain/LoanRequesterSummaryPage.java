package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;

public record LoanRequesterSummaryPage(
        List<LoanRequesterSummary> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
