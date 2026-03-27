package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import com.example.backend.testsupport.ParagraphFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft9MainParagraphCheckerTest {

    @Test
    void noIssues_whenSpacingIndentAndJustifyMatch() {
        List<String> issues = Ft9MainParagraphChecker.check(List.of(ParagraphFixtures.bodyTimes12Black()));
        assertTrue(issues.isEmpty(), issues::toString);
    }

    @Test
    void issue_whenLineSpacingNotOneAndHalf() {
        ParagraphInfo p =
                ParagraphFixtures.bodyBuilder()
                        .fontName("Times New Roman")
                        .fontSizePt(12.0)
                        .lineSpacing(1.0)
                        .firstLineIndentCm(1.25)
                        .alignment("BOTH")
                        .build();
        List<String> issues = Ft9MainParagraphChecker.check(List.of(p));
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("ФТ-9") && issues.get(0).contains("межстрочный"));
    }

    @Test
    void issue_whenFirstLineIndentWrong() {
        ParagraphInfo p =
                ParagraphFixtures.bodyBuilder()
                        .fontName("Times New Roman")
                        .fontSizePt(12.0)
                        .lineSpacing(1.5)
                        .firstLineIndentCm(2.5)
                        .alignment("BOTH")
                        .build();
        List<String> issues = Ft9MainParagraphChecker.check(List.of(p));
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("красная строка") || s.contains("отступ")));
    }

    @Test
    void issue_whenNotJustified() {
        ParagraphInfo p =
                ParagraphFixtures.bodyBuilder()
                        .fontName("Times New Roman")
                        .fontSizePt(12.0)
                        .lineSpacing(1.5)
                        .firstLineIndentCm(1.25)
                        .alignment("LEFT")
                        .build();
        List<String> issues = Ft9MainParagraphChecker.check(List.of(p));
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("выравнивание") || s.contains("ширине")));
    }
}
