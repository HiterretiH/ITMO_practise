package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.Map;

/** Межстрочный интервал и абзацный отступ основного текста (ФТ-9). */
public record Ft9ParagraphParams(
        double lineSpacing, double lineSpacingEps, double firstLineIndentCm, double firstLineIndentEps) {

    public static Ft9ParagraphParams defaults() {
        return new Ft9ParagraphParams(1.5, 0.06, 1.25, 0.2);
    }

    public static Ft9ParagraphParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft9ParagraphParams fromMap(Map<String, Object> m) {
        return new Ft9ParagraphParams(
                JsonRuleParams.doubleValue(m, "lineSpacing", 1.5),
                JsonRuleParams.doubleValue(m, "lineSpacingEps", 0.06),
                JsonRuleParams.doubleValue(m, "firstLineIndentCm", 1.25),
                JsonRuleParams.doubleValue(m, "firstLineIndentEps", 0.2));
    }
}
