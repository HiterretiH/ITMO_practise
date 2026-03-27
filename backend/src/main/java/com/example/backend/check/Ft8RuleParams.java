package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.Map;

/** Допустимый кегль основного текста (ФТ-8, п. 4.2). Гарнитура — Times New Roman (логика в коде). */
public record Ft8RuleParams(double minPt, double maxRecommendedPt, double ptEps) {

    public static Ft8RuleParams defaults() {
        return new Ft8RuleParams(12.0, 14.0, 0.05);
    }

    public static Ft8RuleParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft8RuleParams fromMap(Map<String, Object> m) {
        return new Ft8RuleParams(
                JsonRuleParams.doubleValue(m, "minPt", 12.0),
                JsonRuleParams.doubleValue(m, "maxRecommendedPt", 14.0),
                JsonRuleParams.doubleValue(m, "ptEps", 0.05));
    }
}
