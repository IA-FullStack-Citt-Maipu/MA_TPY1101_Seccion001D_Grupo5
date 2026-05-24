package com.panol_project.backendpanol.modules.loan.domain;

import java.util.List;

public record LoanSummaryPage(
        List<LoanSummaryView> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
