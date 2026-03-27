package com.example.backend.check;

import com.example.backend.domain.DocumentPageSettings;
import com.example.backend.domain.PageMargins;
import com.example.backend.domain.ParagraphInfo;
import com.example.backend.domain.SectionPageInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft10PageMarginsCheckerTest {

    @Test
    void issue_whenLeftMarginTooSmall() {
        PageMargins bad = PageMargins.builder().leftCm(2.0).rightCm(1.2).topCm(2.0).bottomCm(2.0).build();
        DocumentPageSettings settings =
                DocumentPageSettings.builder()
                        .sections(List.of(SectionPageInfo.builder().sectionIndex(0).margins(bad).build()))
                        .build();
        List<String> issues =
                Ft10PageMarginsChecker.check(
                        settings, PageMargins.builder().build(), List.of(ParagraphInfo.builder().pageIndex(1).build()), List.of());
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("ФТ-10") && s.contains("левое")));
    }

    @Test
    void noIssues_whenMarginsMatchNorm() {
        PageMargins ok = PageMargins.builder().leftCm(3.0).rightCm(1.2).topCm(2.0).bottomCm(2.0).build();
        DocumentPageSettings settings =
                DocumentPageSettings.builder()
                        .sections(List.of(SectionPageInfo.builder().sectionIndex(0).margins(ok).build()))
                        .build();
        List<String> issues =
                Ft10PageMarginsChecker.check(
                        settings, ok, List.of(ParagraphInfo.builder().pageIndex(1).build()), List.of());
        assertTrue(issues.stream().noneMatch(s -> s.contains("левое поле") && s.contains("ожидается 30")));
    }
}
