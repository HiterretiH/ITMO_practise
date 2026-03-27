package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft6SectionStartCheckerTest {

    @Test
    void issue_whenHeadingStartsOnSamePageAsPreviousParagraphEnd() {
        List<ParagraphInfo> paras =
                List.of(
                        ParagraphInfo.builder()
                                .text("Какой-то текст перед разделом.")
                                .outlineLevel(null)
                                .pageIndex(1)
                                .pageEndIndex(1)
                                .inTable(false)
                                .containsFormula(false)
                                .build(),
                        ParagraphInfo.builder()
                                .text("ВВЕДЕНИЕ")
                                .outlineLevel(0)
                                .pageIndex(1)
                                .pageEndIndex(1)
                                .inTable(false)
                                .containsFormula(false)
                                .build());
        List<String> issues = Ft6SectionStartChecker.check(paras);
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("ФТ-6") && issues.get(0).contains("новой страницы"));
    }

    @Test
    void noIssue_whenHeadingStartsAfterPreviousPageEnd() {
        List<ParagraphInfo> paras =
                List.of(
                        ParagraphInfo.builder()
                                .text("Текст на стр. 1.")
                                .outlineLevel(null)
                                .pageIndex(1)
                                .pageEndIndex(1)
                                .inTable(false)
                                .containsFormula(false)
                                .build(),
                        ParagraphInfo.builder()
                                .text("ВВЕДЕНИЕ")
                                .outlineLevel(0)
                                .pageIndex(2)
                                .pageEndIndex(2)
                                .inTable(false)
                                .containsFormula(false)
                                .build());
        List<String> issues = Ft6SectionStartChecker.check(paras);
        assertTrue(issues.isEmpty(), issues::toString);
    }

    @Test
    void isFt6Target_true_forFixedSectionTitle() {
        ParagraphInfo p =
                ParagraphInfo.builder().text("ВВЕДЕНИЕ").outlineLevel(0).pageIndex(1).build();
        assertTrue(Ft6SectionStartChecker.isFt6Target(p));
    }
}
