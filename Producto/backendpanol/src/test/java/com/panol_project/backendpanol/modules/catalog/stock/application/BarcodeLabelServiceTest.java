package com.panol_project.backendpanol.modules.catalog.stock.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.panol_project.backendpanol.modules.catalog.implement.application.contract.ImplementLookupContract;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockRepository;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BarcodeLabelServiceTest {

    @Mock
    private ImplementLookupContract implementLookupContract;

    @Mock
    private StockRepository stockRepository;

    @Test
    void scopeIndividualDebeFallarSiImplementoNoEsIndividual() {
        UUID implementUuid = UUID.randomUUID();
        when(implementLookupContract.obtenerImplementoParaStock(implementUuid))
                .thenReturn(new ImplementLookupContract.ImplementLookupSummary(
                        implementUuid,
                        "Guantes",
                        "ABC123",
                        "consumable"
                ));

        BarcodeLabelService service = new BarcodeLabelService(implementLookupContract, stockRepository);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.generateLabelsPdf(implementUuid, "INDIVIDUAL", 1, null));

        assertEquals("LABEL_SCOPE_INVALID", ex.getCode());
        verify(implementLookupContract).obtenerImplementoParaStock(implementUuid);
    }
}
