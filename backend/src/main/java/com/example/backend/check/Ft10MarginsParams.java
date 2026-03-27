package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.Map;

/** Поля страницы в см (ФТ-10): левое 30 мм = 3 см и т.д. */
public record Ft10MarginsParams(
        double leftCm, double rightMinCm, double rightMaxCm, double topCm, double bottomCm, double epsCm) {

    public static Ft10MarginsParams defaults() {
        return new Ft10MarginsParams(3.0, 1.0, 1.5, 2.0, 2.0, 0.05);
    }

    public static Ft10MarginsParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft10MarginsParams fromMap(Map<String, Object> m) {
        return new Ft10MarginsParams(
                JsonRuleParams.doubleValue(m, "leftCm", 3.0),
                JsonRuleParams.doubleValue(m, "rightMinCm", 1.0),
                JsonRuleParams.doubleValue(m, "rightMaxCm", 1.5),
                JsonRuleParams.doubleValue(m, "topCm", 2.0),
                JsonRuleParams.doubleValue(m, "bottomCm", 2.0),
                JsonRuleParams.doubleValue(m, "epsCm", 0.05));
    }
}
