package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft4RequiredSectionsCheckerTest {

    @Test
    void emptyDocument_hasMultipleMissingSectionIssues() {
        List<String> issues = Ft4RequiredSectionsChecker.check(List.of());
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("ФТ-4") && s.contains("СОДЕРЖАНИЕ")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("ПРИЛОЖЕНИЕ")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("глава")));
    }

    @Test
    void minimalCompliantSet_hasNoFt4Issues() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(h0("СОДЕРЖАНИЕ"));
        paras.add(h0("ВВЕДЕНИЕ"));
        paras.add(h0("ЗАКЛЮЧЕНИЕ"));
        paras.add(h0("СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ"));
        paras.add(h0("ПРИЛОЖЕНИЕ А"));
        paras.add(h0("1. ГЛАВА ОСНОВНОЙ ЧАСТИ"));
        List<String> issues = Ft4RequiredSectionsChecker.check(paras);
        assertTrue(issues.stream().noneMatch(s -> s.startsWith("ФТ-4")), issues::toString);
    }

    private static ParagraphInfo h0(String titleCaps) {
        return ParagraphInfo.builder()
                .text(titleCaps)
                .outlineLevel(0)
                .inTable(false)
                .containsFormula(false)
                .pageIndex(1)
                .build();
    }
}
