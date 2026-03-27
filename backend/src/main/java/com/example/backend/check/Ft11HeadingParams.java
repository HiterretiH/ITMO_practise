package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.Map;

/** Ориентир красной строки заголовков разделов (ФТ-11, согласовано с основным текстом). */
public record Ft11HeadingParams(double firstLineIndentCmExpected, double firstLineIndentEps) {

    public static Ft11HeadingParams defaults() {
        return new Ft11HeadingParams(1.25, 0.35);
    }

    public static Ft11HeadingParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft11HeadingParams fromMap(Map<String, Object> m) {
        return new Ft11HeadingParams(
                JsonRuleParams.doubleValue(m, "firstLineIndentCmExpected", 1.25),
                JsonRuleParams.doubleValue(m, "firstLineIndentEps", 0.35));
    }
}
