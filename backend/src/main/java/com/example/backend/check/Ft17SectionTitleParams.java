package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.Map;

/** Точный текст заголовка раздела списка сокращений (ФТ-17). */
public record Ft17SectionTitleParams(String sectionTitle) {

    public static Ft17SectionTitleParams defaults() {
        return new Ft17SectionTitleParams("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ");
    }

    public static Ft17SectionTitleParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft17SectionTitleParams fromMap(Map<String, Object> m) {
        return new Ft17SectionTitleParams(JsonRuleParams.stringValue(m, "sectionTitle", defaults().sectionTitle()));
    }
}
