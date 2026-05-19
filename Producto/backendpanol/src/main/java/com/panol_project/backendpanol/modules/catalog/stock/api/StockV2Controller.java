package com.panol_project.backendpanol.modules.catalog.stock.api;

import com.panol_project.backendpanol.modules.catalog.stock.api.dto.IndividualUpdateV2Request;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.IndividualV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.StockCountersResponse;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.StockDetailV2Response;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.StockEntryRequest;
import com.panol_project.backendpanol.modules.catalog.stock.api.dto.StockMovementV2Request;
import com.panol_project.backendpanol.modules.catalog.stock.application.StockService;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockDetail;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/implements/{implementUuid}/stock")
public class StockV2Controller {

    private final StockService stockService;

    public StockV2Controller(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    StockDetailV2Response getStock(@PathVariable UUID implementUuid) {
        return toV2Response(stockService.getStockDetail(implementUuid));
    }

    @PostMapping("/entries")
    StockDetailV2Response addEntry(@PathVariable UUID implementUuid, @Valid @RequestBody StockEntryRequest request) {
        return toV2Response(stockService.addEntry(
                implementUuid,
                request.quantity(),
                request.assetCodes()
        ));
    }

    @PostMapping("/movements")
    StockDetailV2Response applyMovement(@PathVariable UUID implementUuid, @Valid @RequestBody StockMovementV2Request request) {
        return toV2Response(stockService.applyMovement(
                implementUuid,
                request.movementType(),
                request.quantity(),
                request.individualUuids(),
                request.condition()
        ));
    }

    @PutMapping("/individuals/{individualUuid}")
    StockDetailV2Response updateIndividual(@PathVariable UUID implementUuid, @PathVariable UUID individualUuid, @RequestBody IndividualUpdateV2Request request) {
        return toV2Response(stockService.updateIndividual(
                implementUuid,
                individualUuid,
                request.status(),
                request.condition(),
                request.notes(),
                request.currentLocationUuid(),
                request.active()
        ));
    }

    private StockDetailV2Response toV2Response(StockDetail detail) {
        return new StockDetailV2Response(
                detail.implementUuid(),
                detail.itemType() == null ? null : detail.itemType().literal(),
                new StockCountersResponse(
                        detail.stock().totalStock(),
                        detail.stock().minStock(),
                        detail.stock().available(),
                        detail.stock().reserved(),
                        detail.stock().loaned(),
                        detail.stock().damaged()
                ),
                detail.individuals().stream().map(item -> new IndividualV2Response(
                        item.uuid(),
                        item.assetCode(),
                        item.status(),
                        item.condition(),
                        item.notes(),
                        item.currentLocationUuid(),
                        item.active()
                )).toList()
        );
    }
}
