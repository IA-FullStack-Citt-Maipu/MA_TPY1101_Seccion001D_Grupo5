package com.panol_project.backendpanol.modules.catalog.stock.api;

import com.panol_project.backendpanol.modules.catalog.stock.api.dto.IndividualStatusSummaryV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.application.StockService;
import com.panol_project.backendpanol.modules.catalog.stock.domain.IndividualStatusSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/inventory/dashboard")
public class InventoryDashboardV2Controller {

    private final StockService stockService;

    public InventoryDashboardV2Controller(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/individual-summary")
    IndividualStatusSummaryV2Response getIndividualSummary() {
        IndividualStatusSummary summary = stockService.getActiveIndividualSummary();
        return new IndividualStatusSummaryV2Response(
                summary.available(),
                summary.loaned(),
                summary.maintenance(),
                summary.damaged(),
                summary.blocked(),
                summary.retired(),
                summary.total()
        );
    }
}
