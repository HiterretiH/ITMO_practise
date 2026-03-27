package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.Map;

/**
 * Пороги отступов списков (ФТ-21). Лимит числа замечаний задаётся в коде ({@link Ft21ListsEnumerationChecker}).
 */
public record Ft21ListParams(double indentCmExpected, double indentCmEpsilon, double indentMinCm) {

    public static Ft21ListParams defaults() {
        return new Ft21ListParams(1.25, 0.35, 0.75);
    }

    public static Ft21ListParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft21ListParams fromMap(Map<String, Object> m) {
        return new Ft21ListParams(
                JsonRuleParams.doubleValue(m, "indentCmExpected", 1.25),
                JsonRuleParams.doubleValue(m, "indentCmEpsilon", 0.35),
                JsonRuleParams.doubleValue(m, "indentMinCm", 0.75));
    }
}
