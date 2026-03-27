package com.example.backend.controller;

import com.example.backend.json.ValidationResult;
import com.example.backend.json.ValidationStatus;
import com.example.backend.service.CheckEngineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/v1")
public class ValidateController {

    private final CheckEngineService checkEngineService;

    public ValidateController(CheckEngineService checkEngineService) {
        this.checkEngineService = checkEngineService;
    }

    @PostMapping(
            value = "/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ValidationResult> validateDocument(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "document.docx";
        }
        try (InputStream inputStream = file.getInputStream()) {
            ValidationResult result = checkEngineService.validate(filename, file.getContentType(), inputStream);
            if (result.getSummary() != null && result.getSummary().getStatus() == ValidationStatus.failed) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
            }
            return ResponseEntity.ok(result);
        }
    }
}
