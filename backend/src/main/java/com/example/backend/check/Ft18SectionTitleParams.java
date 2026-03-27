package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.Map;

/** Точный текст заголовка раздела терминов (ФТ-18). */
public record Ft18SectionTitleParams(String sectionTitle) {

    public static Ft18SectionTitleParams defaults() {
        return new Ft18SectionTitleParams("ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ");
    }

    public static Ft18SectionTitleParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft18SectionTitleParams fromMap(Map<String, Object> m) {
        return new Ft18SectionTitleParams(JsonRuleParams.stringValue(m, "sectionTitle", defaults().sectionTitle()));
    }
}
