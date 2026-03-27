package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft5SectionNumberingCheckerTest {

    @Test
    void issue_whenIntroOrConclusionMissing() {
        List<String> issues =
                Ft5SectionNumberingChecker.check(
                        List.of(
                                ParagraphInfo.builder()
                                        .text("ВВЕДЕНИЕ")
                                        .outlineLevel(0)
                                        .pageIndex(1)
                                        .build()));
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("ФТ-5") && issues.get(0).contains("основную часть"));
    }

    @Test
    void issue_trailingDotAfterNumberBetweenIntroAndConclusion() {
        List<ParagraphInfo> paras =
                List.of(
                        ParagraphInfo.builder().text("ВВЕДЕНИЕ").outlineLevel(0).pageIndex(1).build(),
                        ParagraphInfo.builder().text("1.1. Подраздел").outlineLevel(1).pageIndex(1).build(),
                        ParagraphInfo.builder().text("ЗАКЛЮЧЕНИЕ").outlineLevel(0).pageIndex(1).build());
        List<String> issues = Ft5SectionNumberingChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("точк") && s.contains("1.1.")));
    }
}
