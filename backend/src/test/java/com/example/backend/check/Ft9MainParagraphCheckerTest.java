package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import com.example.backend.testsupport.ParagraphFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void noAlignmentIssue_inTermsDefinitionsSection_leftAlignOk() {
        ParagraphInfo heading =
                ParagraphInfo.builder()
                        .text("ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ")
                        .outlineLevel(0)
                        .pageIndex(1)
                        .build();
        ParagraphInfo termLine =
                ParagraphFixtures.bodyBuilder()
                        .text("API — интерфейс прикладного программирования")
                        .alignment("LEFT")
                        .lineSpacing(1.5)
                        .firstLineIndentCm(0.0)
                        .build();
        List<String> issues = Ft9MainParagraphChecker.check(List.of(heading, termLine));
        assertTrue(issues.stream().noneMatch(s -> s.contains("выравнивание")), issues::toString);
    }

    @Test
    void termsBodyBounds_matchFt18Range_threeTermsBeforeNextHeading() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("СОДЕРЖАНИЕ").outlineLevel(0).build());
        paras.add(
                ParagraphInfo.builder()
                        .text("ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ")
                        .outlineLevel(0)
                        .build());
        paras.add(leftTerm("A — один"));
        paras.add(leftTerm("B — два"));
        paras.add(leftTerm("C — три"));
        paras.add(ParagraphInfo.builder().text("ВВЕДЕНИЕ").outlineLevel(0).build());
        assertArrayEquals(new int[] {2, 4}, Ft18TermsDefinitionsChecker.termsDefinitionsBodyInclusiveIndexBounds(paras));
        List<String> issues = Ft9MainParagraphChecker.check(paras);
        assertTrue(issues.stream().noneMatch(s -> s.contains("ФТ-9")), issues::toString);
    }

    private static ParagraphInfo leftTerm(String text) {
        return ParagraphFixtures.bodyBuilder()
                .text(text)
                .alignment("LEFT")
                .lineSpacing(1.5)
                .firstLineIndentCm(0.0)
                .build();
    }
}
