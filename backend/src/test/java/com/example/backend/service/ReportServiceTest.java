package com.example.backend.service;

import com.example.backend.json.DocumentInfo;
import com.example.backend.json.ErrorItem;
import com.example.backend.json.ErrorLocation;
import com.example.backend.json.ErrorSeverity;
import com.example.backend.json.ErrorType;
import com.example.backend.json.ReportOptions;
import com.example.backend.json.ValidationResult;
import com.example.backend.json.ValidationStatus;
import com.example.backend.json.ValidationSummary;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    private final ReportService reportService = new ReportService();

    @Test
    void buildReport_producesPdfBytesWithSignature() {
        ValidationResult result = ValidationResult.builder()
                .documentInfo(DocumentInfo.builder()
                        .filename("test.docx")
                        .pages(10)
                        .build())
                .summary(ValidationSummary.builder()
                        .totalErrors(1)
                        .criticalErrors(1)
                        .status(ValidationStatus.failed)
                        .build())
                .errors(List.of(ErrorItem.builder()
                        .id(UUID.randomUUID())
                        .type(ErrorType.FONT_MISMATCH)
                        .severity(ErrorSeverity.critical)
                        .location(ErrorLocation.builder()
                                .page(2)
                                .paragraph(5)
                                .build())
                        .description("Основной текст")
                        .expected("Times New Roman 14 pt")
                        .actual("Arial 12 pt")
                        .recommendation("Замените шрифт")
                        .build()))
                .build();

        byte[] pdf = reportService.buildReport(result, ReportOptions.builder().includeRecommendations(true).build());

        assertTrue(pdf.length > 100, "PDF should not be trivially small");
        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), Arrays.copyOfRange(pdf, 0, 4));
    }

    @Test
    void buildReport_emptyErrors_stillPdf() {
        ValidationResult result = ValidationResult.builder()
                .summary(ValidationSummary.builder()
                        .totalErrors(0)
                        .status(ValidationStatus.passed)
                        .build())
                .errors(List.of())
                .build();

        byte[] pdf = reportService.buildReport(result, null);

        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    void buildReport_withoutRecommendations_columnOmitted() {
        ValidationResult result = ValidationResult.builder()
                .errors(List.of(ErrorItem.builder()
                        .id(UUID.randomUUID())
                        .type(ErrorType.MARGIN_MISMATCH)
                        .severity(ErrorSeverity.warning)
                        .description("Поля")
                        .expected("30 мм")
                        .actual("20 мм")
                        .recommendation("Не показывать")
                        .build()))
                .build();

        byte[] withRec = reportService.buildReport(result, ReportOptions.builder().includeRecommendations(true).build());
        byte[] withoutRec = reportService.buildReport(result, ReportOptions.builder().includeRecommendations(false).build());

        assertTrue(withRec.length > 100 && withoutRec.length > 100);
        assertTrue(withRec.length > withoutRec.length, "PDF with recommendation column should be larger");
    }
}
