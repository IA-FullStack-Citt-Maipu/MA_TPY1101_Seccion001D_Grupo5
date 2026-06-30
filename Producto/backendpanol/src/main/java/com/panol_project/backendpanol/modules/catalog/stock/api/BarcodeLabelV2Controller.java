package com.panol_project.backendpanol.modules.catalog.stock.api;

import com.panol_project.backendpanol.modules.catalog.stock.application.BarcodeLabelService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/implements/{implementUuid}/labels")
public class BarcodeLabelV2Controller {

    private final BarcodeLabelService barcodeLabelService;

    public BarcodeLabelV2Controller(BarcodeLabelService barcodeLabelService) {
        this.barcodeLabelService = barcodeLabelService;
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('COORDINADOR')")
    public ResponseEntity<byte[]> generatePdf(@PathVariable UUID implementUuid, @RequestParam(name = "scope", required = false, defaultValue = "GENERAL") String scope,
            @RequestParam(name = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(name = "individual_uuid", required = false) UUID individualUuid) {
        byte[] pdf = barcodeLabelService.generateLabelsPdf(implementUuid, scope, quantity, individualUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=labels.pdf")
                .body(pdf);
    }
}
