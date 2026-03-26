package com.example.backend.check;

import com.example.backend.domain.FigureInfo;
import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft13FigureCaptionCheckerTest {

    @Test
    void noIssues_whenCaptionValidCenteredBelow() {
        List<ParagraphInfo> paras = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            paras.add(ParagraphInfo.builder().text("x").alignment("LEFT").build());
        }
        paras.add(ParagraphInfo.builder()
                .text("Рисунок 1 – Схема")
                .alignment("CENTER")
                .build());
        FigureInfo f = FigureInfo.builder()
                .paragraphIndex(5)
                .pageIndex(1)
                .caption("Рисунок 1 – Схема")
                .captionParagraphIndex(6)
                .build();
        assertTrue(Ft13FigureCaptionChecker.check(List.of(f), paras).isEmpty());
    }

    @Test
    void issue_whenCaptionMissing() {
        FigureInfo f = FigureInfo.builder()
                .paragraphIndex(0)
                .pageIndex(1)
                .build();
        assertEquals(1, Ft13FigureCaptionChecker.check(List.of(f), List.of()).size());
    }

    @Test
    void issue_whenFormatWrong() {
        FigureInfo f = FigureInfo.builder()
                .paragraphIndex(0)
                .pageIndex(1)
                .caption("Рис. 1 без тире")
                .captionParagraphIndex(1)
                .build();
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("x").build(),
                ParagraphInfo.builder().text("Рис. 1 без тире").alignment("CENTER").build()
        );
        assertFalse(Ft13FigureCaptionChecker.check(List.of(f), paras).isEmpty());
    }

    @Test
    void issue_whenLetterJInsteadOfNumber_explains() {
        FigureInfo f = FigureInfo.builder()
                .paragraphIndex(61)
                .pageIndex(13)
                .caption("Рисунок j – Фото ИТМО")
                .captionParagraphIndex(62)
                .build();
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("x").build(),
                ParagraphInfo.builder().text("Рисунок j – Фото ИТМО").alignment("CENTER").build()
        );
        List<String> issues = Ft13FigureCaptionChecker.check(List.of(f), paras);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("стр. 13"));
        assertTrue(issues.get(0).contains("рисунок 1"));
        assertTrue(issues.get(0).contains("буква") || issues.get(0).contains("цифра"));
    }

    @Test
    void issue_whenNotCentered() {
        FigureInfo f = FigureInfo.builder()
                .paragraphIndex(0)
                .pageIndex(1)
                .caption("Рисунок 1 – А")
                .captionParagraphIndex(1)
                .build();
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("img").build(),
                ParagraphInfo.builder().text("Рисунок 1 – А").alignment("LEFT").build()
        );
        List<String> issues = Ft13FigureCaptionChecker.check(List.of(f), paras);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("центр"));
    }

    @Test
    void issue_whenCaptionAboveImage() {
        FigureInfo f = FigureInfo.builder()
                .paragraphIndex(5)
                .pageIndex(1)
                .caption("Рисунок 1 – А")
                .captionParagraphIndex(3)
                .build();
        List<ParagraphInfo> paras = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            paras.add(ParagraphInfo.builder().text("x").alignment("CENTER").build());
        }
        List<String> issues = Ft13FigureCaptionChecker.check(List.of(f), paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("под рисунком")));
    }
}
