package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.Map;

/** Точный текст заголовка списка источников (ФТ-20). */
public record Ft20SectionTitleParams(String sectionTitle) {

    public static Ft20SectionTitleParams defaults() {
        return new Ft20SectionTitleParams("СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ");
    }

    public static Ft20SectionTitleParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft20SectionTitleParams fromMap(Map<String, Object> m) {
        return new Ft20SectionTitleParams(JsonRuleParams.stringValue(m, "sectionTitle", defaults().sectionTitle()));
    }
}
