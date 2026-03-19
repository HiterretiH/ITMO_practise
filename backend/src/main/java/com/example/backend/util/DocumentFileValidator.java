package com.example.backend.util;

import com.example.backend.exception.ValidationException;

import java.util.Set;

public final class DocumentFileValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public static void validate(String filename, String contentType) {
        if (filename == null || filename.isBlank()) {
            throw new ValidationException("Имя файла отсутствует.");
        }
        String ext = getExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException(
                    "Недопустимый формат файла. Разрешены только .doc и .docx. Получено: " + ext);
        }
        if (contentType != null && !contentType.isBlank()) {
            String baseType = contentType.split(";")[0].trim().toLowerCase();
            if (!ALLOWED_MIME_TYPES.contains(baseType)) {
                throw new ValidationException(
                        "Недопустимый тип содержимого. Ожидается документ Word. Получено: " + baseType);
            }
        }
    }

    public static String getExtension(String filename) {
        int i = filename.lastIndexOf('.');
        return i > 0 ? filename.substring(i + 1) : "";
    }

    public static boolean isDocx(String filename) {
        return "docx".equalsIgnoreCase(getExtension(filename));
    }

    public static boolean isDoc(String filename) {
        return "doc".equalsIgnoreCase(getExtension(filename));
    }

    private DocumentFileValidator() {}
}
