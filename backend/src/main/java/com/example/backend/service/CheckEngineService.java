package com.example.backend.service;

import com.example.backend.check.runner.CheckExecutionResult;
import com.example.backend.check.runner.VkrChecksRunner;
import com.example.backend.config.checks.ChecksConfigurationLoader;
import com.example.backend.domain.DocumentStructure;
import com.example.backend.domain.ParagraphInfo;
import com.example.backend.json.DocumentInfo;
import com.example.backend.json.ErrorItem;
import com.example.backend.json.ErrorSeverity;
import com.example.backend.json.ValidationResult;
import com.example.backend.json.ValidationStatus;
import com.example.backend.json.ValidationSummary;
import com.example.backend.mapper.ValidationIssueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Загрузка документа и прогон всех включённых в {@code checks-config.json} проверок (ФТ-4 … ФТ-21).
 */
@Service
public class CheckEngineService {

    private static final Logger log = LoggerFactory.getLogger(CheckEngineService.class);

    private final DocxLoadService docxLoadService;
    private final ChecksConfigurationLoader checksConfigurationLoader;

    public CheckEngineService(DocxLoadService docxLoadService, ChecksConfigurationLoader checksConfigurationLoader) {
        this.docxLoadService = docxLoadService;
        this.checksConfigurationLoader = checksConfigurationLoader;
    }

    public List<CheckExecutionResult> validateDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "document.docx";
        }
        DocumentStructure structure = docxLoadService.load(filename, file.getContentType(), file.getInputStream());
        return VkrChecksRunner.run(structure, checksConfigurationLoader.load());
    }

    public ValidationResult validate(String filename, String contentType, InputStream inputStream) {
        long t0 = System.nanoTime();
        DocumentStructure structure = docxLoadService.load(filename, contentType, inputStream);
        long loadMs = (System.nanoTime() - t0) / 1_000_000L;

        long t1 = System.nanoTime();
        List<CheckExecutionResult> results = VkrChecksRunner.run(structure, checksConfigurationLoader.load());
        long checksMs = (System.nanoTime() - t1) / 1_000_000L;

        log.info(
                "validation pipeline: file={} load={}ms checks={}ms paragraphs={}",
                filename,
                loadMs,
                checksMs,
                structure.getParagraphs() != null ? structure.getParagraphs().size() : 0);

        List<String> rawIssues = new ArrayList<>();
        for (CheckExecutionResult r : results) {
            if (r.ran()) {
                rawIssues.addAll(r.issues());
            }
        }
        List<ErrorItem> errors = new ArrayList<>(rawIssues.size());
        for (String line : rawIssues) {
            errors.add(ValidationIssueMapper.toErrorItem(line));
        }
        int criticalCount = (int) errors.stream()
                .filter(e -> e.getSeverity() == ErrorSeverity.critical)
                .count();
        ValidationStatus status;
        if (errors.isEmpty()) {
            status = ValidationStatus.passed;
        } else if (criticalCount > 0) {
            status = ValidationStatus.failed;
        } else {
            status = ValidationStatus.warning;
        }
        List<ParagraphInfo> paragraphs = structure.getParagraphs();
        int pages = estimatePages(paragraphs);
        DocumentInfo documentInfo = DocumentInfo.builder()
                .filename(filename)
                .pages(pages)
                .paragraphsChecked(paragraphs.size())
                .build();
        ValidationSummary summary = ValidationSummary.builder()
                .totalErrors(errors.size())
                .criticalErrors(criticalCount)
                .status(status)
                .build();
        return ValidationResult.builder()
                .documentInfo(documentInfo)
                .summary(summary)
                .errors(errors)
                .build();
    }

    private static int estimatePages(List<ParagraphInfo> paragraphs) {
        int max = 1;
        for (ParagraphInfo p : paragraphs) {
            if (p.getPageEndIndex() != null) {
                max = Math.max(max, p.getPageEndIndex());
            } else if (p.getPageIndex() != null) {
                max = Math.max(max, p.getPageIndex());
            }
        }
        return max;
    }
}
