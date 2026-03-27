package com.example.backend.service;

import com.example.backend.check.runner.CheckExecutionResult;
import com.example.backend.check.runner.VkrChecksRunner;
import com.example.backend.config.checks.ChecksConfigurationLoader;
import com.example.backend.domain.DocumentStructure;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Загрузка документа и прогон всех включённых в {@code checks-config.json} проверок (ФТ-4 … ФТ-21).
 */
@Service
public class CheckEngineService {

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
}
