package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.List;
import java.util.Map;

/** Заголовки обязательных разделов (ФТ-4). */
public record Ft4SectionTitlesParams(List<String> fixedSectionTitles) {

    public static Ft4SectionTitlesParams defaults() {
        return new Ft4SectionTitlesParams(
                List.of(
                        "СОДЕРЖАНИЕ",
                        "ВВЕДЕНИЕ",
                        "ЗАКЛЮЧЕНИЕ",
                        "СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ"));
    }

    public static Ft4SectionTitlesParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft4SectionTitlesParams fromMap(Map<String, Object> m) {
        List<String> list =
                JsonRuleParams.stringList(m, "fixedSectionTitles", defaults().fixedSectionTitles());
        return new Ft4SectionTitlesParams(List.copyOf(list));
    }
}
