package com.panol_project.backendpanol.modules.catalog.stock.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.panol_project.backendpanol.modules.catalog.implement.application.contract.ImplementLookupContract;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockItemType;
import com.panol_project.backendpanol.modules.catalog.stock.domain.StockRepository;
import com.panol_project.backendpanol.shared.error.BadRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BarcodeLabelService {

    private static final float PAGE_MARGIN = 28f;
    private static final float MM_TO_PT = 72f / 25.4f;
    private static final float LABEL_WIDTH = 40f * MM_TO_PT;
    private static final float LABEL_HEIGHT = 30f * MM_TO_PT;
    private static final float COL_GAP = 10f;
    private static final float ROW_GAP = 10f;
    private static final int MAX_LABELS = 500;
    private static final int BARCODE_WIDTH = 150;
    private static final int BARCODE_HEIGHT = 42;

    private final ImplementLookupContract implementLookupContract;
    private final StockRepository stockRepository;

    public BarcodeLabelService(ImplementLookupContract implementLookupContract, StockRepository stockRepository) {
        this.implementLookupContract = implementLookupContract;
        this.stockRepository = stockRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generateLabelsPdf(UUID implementUuid, String scopeRaw, Integer quantity, UUID individualUuid) {
        ImplementLookupContract.ImplementLookupSummary implemento = implementLookupContract.obtenerImplementoParaStock(implementUuid);
        LabelScope scope = LabelScope.from(scopeRaw);
        List<LabelData> labels = resolveLabels(implemento, scope, quantity, individualUuid);
        try {
            return renderPdf(labels);
        } catch (IOException ex) {
            throw new BadRequestException("LABEL_PDF_BUILD_ERROR", "No se pudo generar el PDF de etiquetas");
        }
    }

    private List<LabelData> resolveLabels(ImplementLookupContract.ImplementLookupSummary implemento, LabelScope scope, Integer quantity, UUID individualUuid) {
        if (scope == LabelScope.INDIVIDUAL) {
            StockItemType itemType = StockItemType.fromLiteral(implemento.itemTypeLiteral()).orElse(null);
            if (itemType != StockItemType.INDIVIDUAL) {
                throw new BadRequestException(
                        "LABEL_SCOPE_INVALID",
                        "Solo los implementos de tipo individual permiten etiquetas individuales"
                );
            }
            if (individualUuid != null) {
                var selected = stockRepository.findActiveIndividualsByUuids(implemento.uuid(), List.of(individualUuid));
                if (selected.isEmpty()) {
                    throw new BadRequestException("LABEL_INDIVIDUAL_NOT_FOUND", "La unidad individual no existe o no esta activa");
                }
                var individual = selected.getFirst();
                String code = normalizeCode(individual.assetCode(), "IND-" + individual.uuid());
                return List.of(new LabelData(implemento.name(), code, "Unidad individual"));
            }

            int qty = normalizeQuantity(quantity);
            var individuals = stockRepository.findActiveIndividualsByImplementUuid(implemento.uuid());
            if (qty > individuals.size()) {
                throw new BadRequestException(
                        "LABEL_QUANTITY_EXCEEDS_INDIVIDUALS",
                        "La cantidad solicitada supera las unidades individuales activas disponibles"
                );
            }
            List<LabelData> labels = new ArrayList<>();
            for (int i = 0; i < qty; i++) {
                var individual = individuals.get(i);
                String code = normalizeCode(individual.assetCode(), "IND-" + individual.uuid());
                labels.add(new LabelData(implemento.name(), code, "Unidad individual"));
            }
            return labels;
        }

        if (individualUuid != null) {
            throw new BadRequestException("LABEL_SCOPE_GENERAL_ONLY", "individual_uuid solo aplica para scope INDIVIDUAL");
        }
        int qty = normalizeQuantity(quantity);
        String generalCode = normalizeCode(implemento.barcode(), "IMP-" + implemento.uuid());
        List<LabelData> labels = new ArrayList<>();
        for (int i = 0; i < qty; i++) {
            labels.add(new LabelData(implemento.name(), generalCode, "Implemento general"));
        }
        return labels;
    }

    private int normalizeQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("LABEL_QUANTITY_INVALID", "quantity debe ser un entero positivo");
        }
        if (quantity > MAX_LABELS) {
            throw new BadRequestException("LABEL_QUANTITY_TOO_LARGE", "quantity no puede superar " + MAX_LABELS);
        }
        return quantity;
    }

    private String normalizeCode(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return raw.trim();
    }

    private byte[] renderPdf(List<LabelData> labels) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font textFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            int columns = Math.max(1, (int) ((pageWidth - (2 * PAGE_MARGIN) + COL_GAP) / (LABEL_WIDTH + COL_GAP)));
            int rows = Math.max(1, (int) ((pageHeight - (2 * PAGE_MARGIN) + ROW_GAP) / (LABEL_HEIGHT + ROW_GAP)));
            int perPage = columns * rows;

            for (int index = 0; index < labels.size(); index++) {
                if (index > 0 && index % perPage == 0) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    pageWidth = page.getMediaBox().getWidth();
                    pageHeight = page.getMediaBox().getHeight();
                }

                int positionInPage = index % perPage;
                int row = positionInPage / columns;
                int col = positionInPage % columns;
                float x = PAGE_MARGIN + col * (LABEL_WIDTH + COL_GAP);
                float y = pageHeight - PAGE_MARGIN - LABEL_HEIGHT - row * (LABEL_HEIGHT + ROW_GAP);

                var label = labels.get(index);
                drawLabel(document, content, x, y, label, titleFont, textFont);
            }

            content.close();
            document.save(output);
            return output.toByteArray();
        }
    }

    private void drawLabel(
            PDDocument document,
            PDPageContentStream content,
            float x,
            float y,
            LabelData label,
            PDType1Font titleFont,
            PDType1Font textFont
    ) throws IOException {
        content.setLineWidth(0.8f);
        content.addRect(x, y, LABEL_WIDTH, LABEL_HEIGHT);
        content.stroke();

        drawText(content, titleFont, 8.8f, x + 5, y + LABEL_HEIGHT - 13, truncate(label.name(), 32));
        drawText(content, textFont, 7.8f, x + 5, y + LABEL_HEIGHT - 24, truncate(label.scopeDescription(), 34));

        BufferedImage barcodeImage = generateCode128(label.code(), BARCODE_WIDTH, BARCODE_HEIGHT);
        PDImageXObject image = LosslessFactory.createFromImage(document, barcodeImage);
        content.drawImage(image, x + 10, y + 18, LABEL_WIDTH - 20, 32);

        drawText(content, textFont, 8.2f, x + 5, y + 7, truncate(label.code(), 34));
    }

    private void drawText(PDPageContentStream content, PDType1Font font, float fontSize, float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private BufferedImage generateCode128(String value, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix bitMatrix = new MultiFormatWriter().encode(value, BarcodeFormat.CODE_128, width, height, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception ex) {
            throw new BadRequestException("BARCODE_BUILD_ERROR", "No se pudo generar el codigo de barras para: " + value);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private record LabelData(String name, String code, String scopeDescription) {
    }

    private enum LabelScope {
        GENERAL,
        INDIVIDUAL;

        static LabelScope from(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return GENERAL;
            }
            String normalized = raw.trim().toUpperCase();
            if ("GENERAL".equals(normalized)) {
                return GENERAL;
            }
            if ("INDIVIDUAL".equals(normalized)) {
                return INDIVIDUAL;
            }
            throw new BadRequestException("LABEL_SCOPE_INVALID", "scope debe ser GENERAL o INDIVIDUAL");
        }
    }
}
