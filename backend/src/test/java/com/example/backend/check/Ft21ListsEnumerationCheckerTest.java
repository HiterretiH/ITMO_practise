package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft21ListsEnumerationCheckerTest {

    @Test
    void noMixedMarkerIssues_whenUniformDecimalAdjacentSameNumId() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(
                ParagraphInfo.builder()
                        .text("Первый пункт перечисления достаточно длинный.")
                        .numberingListParagraph(true)
                        .numberingNumId(1)
                        .numberingIlvl(0)
                        .listNumberingFmt("decimal")
                        .build());
        paras.add(
                ParagraphInfo.builder()
                        .text("Второй пункт перечисления достаточно длинный.")
                        .numberingListParagraph(true)
                        .numberingNumId(1)
                        .numberingIlvl(0)
                        .listNumberingFmt("decimal")
                        .build());
        List<String> issues = Ft21ListsEnumerationChecker.check(paras);
        assertTrue(issues.stream().noneMatch(s -> s.contains("смешаны") || s.contains("разные маркеры")));
    }

    @Test
    void issue_whenMixedFormatsAcrossAllLevelsInOneList() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(
                ParagraphInfo.builder()
                        .text("1 Уровень 0")
                        .numberingListParagraph(true)
                        .numberingNumId(7)
                        .numberingIlvl(0)
                        .listNumberingFmt("decimal")
                        .build());
        paras.add(
                ParagraphInfo.builder()
                        .text("▪ Маркер")
                        .numberingListParagraph(true)
                        .numberingNumId(7)
                        .numberingIlvl(2)
                        .listNumberingFmt("bullet")
                        .build());
        List<String> issues = Ft21ListsEnumerationChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("по всему списку") && s.contains("смешаны")));
    }

    @Test
    void issue_whenDifferentNumIdsAdjacent_stillOneLogicalList() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(
                ParagraphInfo.builder()
                        .text("Пункт с одним numId в Word.")
                        .numberingListParagraph(true)
                        .numberingNumId(10)
                        .numberingIlvl(0)
                        .listNumberingFmt("decimal")
                        .build());
        paras.add(
                ParagraphInfo.builder()
                        .text("Следующий пункт с другим numId, но подряд.")
                        .numberingListParagraph(true)
                        .numberingNumId(11)
                        .numberingIlvl(0)
                        .listNumberingFmt("bullet")
                        .build());
        List<String> issues = Ft21ListsEnumerationChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("по всему списку") && s.contains("смешаны")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("10, 11") && s.contains("несколько numId")));
    }

    @Test
    void listContext_includesStartParagraphNumber() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(
                ParagraphInfo.builder()
                        .text("Обычный текст.")
                        .numberingListParagraph(false)
                        .build());
        paras.add(
                ParagraphInfo.builder()
                        .text("1 Уровень 0")
                        .numberingListParagraph(true)
                        .numberingNumId(7)
                        .numberingIlvl(0)
                        .listNumberingFmt("decimal")
                        .build());
        paras.add(
                ParagraphInfo.builder()
                        .text("▪ Маркер")
                        .numberingListParagraph(true)
                        .numberingNumId(7)
                        .numberingIlvl(2)
                        .listNumberingFmt("bullet")
                        .build());
        List<String> issues = Ft21ListsEnumerationChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("начинается с абзаца №2")));
    }

    @Test
    void issue_lastLevel0Item_showsLastWordNotSingleChar_whenNoClosingDot() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(
                ParagraphInfo.builder()
                        .text("Первый пункт перечисления достаточно длинный.")
                        .numberingListParagraph(true)
                        .numberingNumId(1)
                        .numberingIlvl(0)
                        .listNumberingFmt("decimal")
                        .build());
        paras.add(
                ParagraphInfo.builder()
                        .text("Второй пункт заканчивается словом Рофлс")
                        .numberingListParagraph(true)
                        .numberingNumId(1)
                        .numberingIlvl(0)
                        .listNumberingFmt("decimal")
                        .build());
        List<String> issues = Ft21ListsEnumerationChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("последнее слово без завершающей точки: «Рофлс»")));
    }

    @Test
    void issue_whenMixedMarkersSameLevel() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(
                ParagraphInfo.builder()
                        .text("Первый")
                        .numberingListParagraph(true)
                        .numberingNumId(1)
                        .numberingIlvl(0)
                        .listNumberingFmt("decimal")
                        .build());
        paras.add(
                ParagraphInfo.builder()
                        .text("Второй")
                        .numberingListParagraph(true)
                        .numberingNumId(1)
                        .numberingIlvl(0)
                        .listNumberingFmt("bullet")
                        .build());
        List<String> issues = Ft21ListsEnumerationChecker.check(paras);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("разные маркеры")));
    }
}
