package com.example.backend.check;

import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.JsonRuleParams;

import java.util.List;
import java.util.Map;

/** Дополнительные разделы по п. 3.2 (ФТ-16). */
public record Ft16OptionalSectionsParams(List<String> optionalSectionTitles) {

    public static Ft16OptionalSectionsParams defaults() {
        return new Ft16OptionalSectionsParams(
                List.of(
                        "СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ",
                        "ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ",
                        "СПИСОК ИЛЛЮСТРАТИВНОГО МАТЕРИАЛА"));
    }

    public static Ft16OptionalSectionsParams fromRule(CheckRuleDefinition rule) {
        if (rule == null || rule.params() == null || rule.params().isEmpty()) {
            return defaults();
        }
        return fromMap(rule.params());
    }

    public static Ft16OptionalSectionsParams fromMap(Map<String, Object> m) {
        List<String> list =
                JsonRuleParams.stringList(m, "optionalSectionTitles", defaults().optionalSectionTitles());
        return new Ft16OptionalSectionsParams(List.copyOf(list));
    }
}
