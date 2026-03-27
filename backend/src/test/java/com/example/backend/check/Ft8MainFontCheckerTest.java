package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import com.example.backend.testsupport.ParagraphFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft8MainFontCheckerTest {

    @Test
    void noIssues_whenBodyParagraphCompliant() {
        List<String> issues = Ft8MainFontChecker.check(List.of(ParagraphFixtures.bodyTimes12Black()));
        assertTrue(issues.isEmpty(), issues::toString);
    }

    @Test
    void issue_whenRunFlagsTnrViolation() {
        ParagraphInfo p =
                ParagraphFixtures.bodyBuilder()
                        .fontName("Times New Roman")
                        .fontSizePt(12.0)
                        .colorHex("000000")
                        .runFontViolatesTnr(true)
                        .ft8NonTnrFontsFound("Arial")
                        .build();
        List<String> issues = Ft8MainFontChecker.check(List.of(p));
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("ФТ-8") && issues.get(0).contains("шрифт"));
    }

    @Test
    void issue_whenParagraphFontNotTnr() {
        ParagraphInfo p =
                ParagraphFixtures.bodyBuilder()
                        .fontName("Arial")
                        .fontSizePt(12.0)
                        .colorHex("000000")
                        .runFontViolatesTnr(false)
                        .build();
        List<String> issues = Ft8MainFontChecker.check(List.of(p));
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("Times New Roman")));
    }

    @Test
    void issue_whenFontTooSmall() {
        ParagraphInfo p =
                ParagraphFixtures.bodyBuilder()
                        .fontName("Times New Roman")
                        .fontSizePt(10.0)
                        .colorHex("000000")
                        .runFontViolatesTnr(false)
                        .runFontSizeViolates(false)
                        .build();
        List<String> issues = Ft8MainFontChecker.check(List.of(p));
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("кегль") || s.contains("мелко")));
    }

    @Test
    void issue_whenColorNotBlack() {
        ParagraphInfo p =
                ParagraphFixtures.bodyBuilder()
                        .fontName("Times New Roman")
                        .fontSizePt(12.0)
                        .colorHex("FF0000")
                        .runFontViolatesTnr(false)
                        .runColorViolatesBlack(false)
                        .build();
        List<String> issues = Ft8MainFontChecker.check(List.of(p));
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("цвет") || s.contains("чёрн")));
    }

    @Test
    void isTimesNewRoman_acceptsNormalizedName() {
        assertTrue(Ft8MainFontChecker.isTimesNewRoman("Times New Roman"));
        assertTrue(Ft8MainFontChecker.isTimesNewRoman("times new roman"));
    }

    @Test
    void isBlackColor_acceptsHexBlack() {
        assertTrue(Ft8MainFontChecker.isBlackColor("#000000"));
        assertTrue(Ft8MainFontChecker.isBlackColor("000000"));
    }
}
