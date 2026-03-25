package com.example.backend.controller;

import com.example.backend.json.DocumentInfo;
import com.example.backend.json.ErrorItem;
import com.example.backend.json.ErrorLocation;
import com.example.backend.json.ErrorSeverity;
import com.example.backend.json.ErrorType;
import com.example.backend.json.ValidationResult;
import com.example.backend.json.ValidationStatus;
import com.example.backend.json.ValidationSummary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class ValidateController {

    @PostMapping(
            value = "/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ValidationResult> validateDocument(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "diploma_example.docx";
        }
        return ResponseEntity.ok(sampleValidationResult(filename));
    }

    private static ValidationResult sampleValidationResult(String filename) {
        List<ErrorItem> errors = List.of(
                ErrorItem.builder()
                        .id(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"))
                        .type(ErrorType.FONT_MISMATCH)
                        .severity(ErrorSeverity.critical)
                        .location(ErrorLocation.builder()
                                .page(3)
                                .paragraph(12)
                                .element(null)
                                .build())
                        .description("Основной шрифт документа должен быть Times New Roman.")
                        .expected("Times New Roman, 14 pt, черный")
                        .actual("Arial, 12 pt")
                        .recommendation("Выделите весь текст (Ctrl+A) и установите шрифт Times New Roman, размер 14.")
                        .build(),
                ErrorItem.builder()
                        .id(UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a12"))
                        .type(ErrorType.MISSING_SECTION)
                        .severity(ErrorSeverity.critical)
                        .location(ErrorLocation.builder()
                                .page(null)
                                .paragraph(null)
                                .element("СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ")
                                .build())
                        .description("В документе не обнаружен обязательный раздел.")
                        .expected("Заголовок раздела в прописных буквах")
                        .actual("Раздел отсутствует")
                        .recommendation("Добавьте раздел «СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ» перед приложениями.")
                        .build(),
                ErrorItem.builder()
                        .id(UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a13"))
                        .type(ErrorType.MARGIN_MISMATCH)
                        .severity(ErrorSeverity.warning)
                        .location(ErrorLocation.builder()
                                .page(1)
                                .paragraph(null)
                                .element(null)
                                .build())
                        .description("Правое поле страницы выходит за допустимый диапазон.")
                        .expected("10–15 мм")
                        .actual("8 мм")
                        .recommendation("Задайте правое поле не менее 10 мм в параметрах страницы.")
                        .build()
        );

        DocumentInfo documentInfo = DocumentInfo.builder()
                .filename(filename)
                .pages(45)
                .paragraphsChecked(320)
                .build();

        ValidationSummary summary = ValidationSummary.builder()
                .totalErrors(3)
                .criticalErrors(2)
                .status(ValidationStatus.failed)
                .build();

        return ValidationResult.builder()
                .documentInfo(documentInfo)
                .summary(summary)
                .errors(errors)
                .build();
    }
}
