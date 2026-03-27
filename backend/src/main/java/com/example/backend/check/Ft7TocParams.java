package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Заголовки раздела оглавления («СОДЕРЖАНИЕ» / «ОГЛАВЛЕНИЕ»), ФТ-7. */
public record Ft7TocParams(List<String> tocSectionTitles) {

    public static Ft7TocParams defaults() {
        return new Ft7TocParams(List.of("СОДЕРЖАНИЕ", "ОГЛАВЛЕНИЕ"));
    }

    public static Ft7TocParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft7TocParams fromMap(Map<String, Object> m) {
        List<String> list = JsonRuleParams.stringList(m, "tocSectionTitles", defaults().tocSectionTitles());
        return new Ft7TocParams(List.copyOf(list));
    }

    /** Как {@link com.example.backend.check.Ft7TocChecker}: верхний регистр, схлопывание пробелов. */
    public Set<String> normalizedTitleKeys() {
        return tocSectionTitles.stream().map(Ft7TocParams::normalizeTitle).collect(Collectors.toSet());
    }

    private static String normalizeTitle(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
