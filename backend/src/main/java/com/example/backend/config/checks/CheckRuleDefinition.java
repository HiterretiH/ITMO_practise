package com.example.backend.config.checks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Одно функциональное требование (ФТ) в конфигурации: включение, метаданные, опциональные {@code params} и отладочный вывод.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckRuleDefinition(
        String id,
        boolean enabled,
        String title,
        String description,
        String reference,
        Map<String, Object> params,
        Boolean logDiagnostics) {

    /** Дополнительные строки в лог (секции, матрица ссылок) для ФТ-17, 18, 20. */
    public boolean logDiagnosticsEffective() {
        return Boolean.TRUE.equals(logDiagnostics);
    }
}
