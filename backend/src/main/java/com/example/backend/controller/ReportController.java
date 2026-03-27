package com.example.backend.controller;

import com.example.backend.json.DocumentInfo;
import com.example.backend.json.ReportGenerationRequest;
import com.example.backend.service.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping(
            value = "/report",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> generateReport(@RequestBody ReportGenerationRequest request) {
        if (request == null || request.getValidationData() == null) {
            throw new IllegalArgumentException("validation_data is required");
        }
        byte[] pdf = reportService.buildReport(request.getValidationData(), request.getOptions());
        String filename = reportFilename(request.getValidationData().getDocumentInfo());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename)
                .build();
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private static String reportFilename(DocumentInfo info) {
        if (info == null || info.getFilename() == null || info.getFilename().isBlank()) {
            return "vkr-report.pdf";
        }
        String name = info.getFilename().trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ._-]", "_");
        if (name.isEmpty()) {
            return "vkr-report.pdf";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".docx")) {
            return name.substring(0, name.length() - 5) + "-report.pdf";
        }
        if (lower.endsWith(".pdf")) {
            return name;
        }
        return name + "-report.pdf";
    }
}
