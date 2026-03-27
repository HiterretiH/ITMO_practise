package com.example.backend.validation;

import com.example.backend.exception.ValidationException;
import com.example.backend.service.DocxLoadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Повреждённые или неполные файлы не должны ронять JVM: ожидается контролируемая
 * {@link ValidationException} при разборе .docx.
 */
class CorruptedDocumentHandlingTest {

    @Test
    @DisplayName("Невалидное содержимое под видом .docx даёт ValidationException")
    void nonDocxBytesRejectedWithValidationException() {
        DocxLoadService loader = new DocxLoadService();
        byte[] garbage = "not-a-real-docx-zip".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () ->
                                loader.load(
                                        "broken.docx",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        new ByteArrayInputStream(garbage)));
        assertTrue(
                ex.getMessage() != null && (ex.getMessage().contains("docx") || ex.getMessage().contains("читать")),
                "Сообщение должно указывать на ошибку чтения документа: " + ex.getMessage());
    }
}
