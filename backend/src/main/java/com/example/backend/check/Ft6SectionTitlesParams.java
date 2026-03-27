package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.List;
import java.util.Map;

/**
 * Заголовки разделов, для которых проверяется начало с новой страницы (ФТ-6). По умолчанию совпадает с ФТ-4
 * (без «ПРИЛОЖЕНИЕ» — оно ищется отдельно по шаблону).
 */
public record Ft6SectionTitlesParams(List<String> fixedSectionTitles) {

    public static Ft6SectionTitlesParams defaults() {
        return new Ft6SectionTitlesParams(
                List.of(
                        "СОДЕРЖАНИЕ",
                        "ВВЕДЕНИЕ",
                        "ЗАКЛЮЧЕНИЕ",
                        "СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ"));
    }

    public static Ft6SectionTitlesParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft6SectionTitlesParams fromMap(Map<String, Object> m) {
        List<String> list =
                JsonRuleParams.stringList(m, "fixedSectionTitles", defaults().fixedSectionTitles());
        return new Ft6SectionTitlesParams(List.copyOf(list));
    }
}
