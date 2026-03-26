package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft15AppendixCheckerTest {

    @Test
    void noIssues_whenAppendixAOnTopCentered() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("Текст перед приложениями")
                .pageIndex(4)
                .pageEndIndex(4)
                .alignment("LEFT")
                .build());

        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ А")
                .pageIndex(5)
                .pageEndIndex(5)
                .alignment("CENTER")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("Дальнейший текст приложения")
                .pageIndex(5)
                .pageEndIndex(5)
                .alignment("JUSTIFY")
                .build());

        assertTrue(Ft15AppendixChecker.check(paras).isEmpty());
    }

    @Test
    void noIssues_whenOnlyAppendixA_centeredOnTop() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder()
                        .text("ПРИЛОЖЕНИЕ А")
                        .pageIndex(6)
                        .pageEndIndex(6)
                        .alignment("CENTER")
                        .build()
        );
        assertTrue(Ft15AppendixChecker.check(paras).isEmpty());
    }

    @Test
    void noIssues_whenSeveralAppendicesAllCenteredOnTop() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("x").pageIndex(1).pageEndIndex(1).alignment("LEFT").build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ А")
                .pageIndex(2)
                .pageEndIndex(2)
                .alignment("CENTER")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ Б")
                .pageIndex(3)
                .pageEndIndex(3)
                .alignment("CENTER")
                .build());
        assertTrue(Ft15AppendixChecker.check(paras).isEmpty());
    }

    @Test
    void issue_whenAppendixBNotCentered() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder()
                        .text("ПРИЛОЖЕНИЕ Б")
                        .pageIndex(6)
                        .pageEndIndex(6)
                        .alignment("LEFT")
                        .build()
        );
        List<String> issues = Ft15AppendixChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("центр")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("порядку") || s.contains("ожидается")));
    }

    @Test
    void issue_whenLetterOrderSkipsAppendixB() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ А")
                .pageIndex(2)
                .pageEndIndex(2)
                .alignment("CENTER")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ В")
                .pageIndex(3)
                .pageEndIndex(3)
                .alignment("CENTER")
                .build());
        List<String> issues = Ft15AppendixChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("порядку") || s.contains("ожидается")));
    }

    @Test
    void issue_whenAppendixANotOnTopOfPage() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("Текст вверху страницы")
                .pageIndex(5)
                .pageEndIndex(5)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ А")
                .pageIndex(5)
                .pageEndIndex(5)
                .alignment("CENTER")
                .build());

        List<String> issues = Ft15AppendixChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("наверху страницы")));
    }

    @Test
    void issue_whenAppendixHeadingNotAllCaps() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder()
                        .text("Приложение А")
                        .pageIndex(7)
                        .pageEndIndex(7)
                        .alignment("CENTER")
                        .build()
        );
        List<String> issues = Ft15AppendixChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("прописными") || s.contains("заглавными")));
    }

    @Test
    void issue_whenAppendixANotCentered() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder()
                        .text("ПРИЛОЖЕНИЕ А")
                        .pageIndex(5)
                        .pageEndIndex(5)
                        .alignment("LEFT")
                        .build()
        );
        List<String> issues = Ft15AppendixChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("центр")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("стр. 5")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("w:jc")));
    }

    /**
     * После «СОДЕРЖАНИЕ» все строки до первого заголовка раздела — оглавление; дальше «ПРИЛОЖЕНИЕ …» проверяется как в тексте.
     */
    @Test
    void noIssues_forTocBlockThenIssuesForRealAppendixAfterStructuralHeading() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("СОДЕРЖАНИЕ")
                .pageIndex(1)
                .pageEndIndex(1)
                .styleName("Диплом - заголовок")
                .outlineLevel(0)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ А\t14")
                .pageIndex(1)
                .pageEndIndex(1)
                .styleName("Диплом - пункт")
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ВВЕДЕНИЕ")
                .pageIndex(2)
                .pageEndIndex(2)
                .styleName("Диплом - заголовок")
                .outlineLevel(0)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ А")
                .pageIndex(10)
                .pageEndIndex(10)
                .alignment("LEFT")
                .build());

        List<String> issues = Ft15AppendixChecker.check(paras);
        assertTrue(issues.stream().anyMatch(s -> s.contains("стр. 10")),
                "замечание должно относиться к абзацу в тексте, не к строке оглавления на стр. 1");
        assertTrue(issues.stream().anyMatch(s -> s.contains("w:jc")));
    }

    /** Пробел + номер страницы (как в ФТ-7), не таб — тоже строка оглавления. */
    @Test
    void noIssues_whenAppendixTocLinesUseSpaceBeforePageNumber() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("СОДЕРЖАНИЕ")
                .pageIndex(1)
                .pageEndIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ А 14")
                .pageIndex(1)
                .pageEndIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПриложениЕ Б 15")
                .pageIndex(1)
                .pageEndIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ Г 16")
                .pageIndex(1)
                .pageEndIndex(1)
                .alignment("LEFT")
                .build());
        assertTrue(Ft15AppendixChecker.check(paras).isEmpty());
    }

    /** Строки оглавления «ПРИЛОЖЕНИЕ … таб номер» не проверяются как заголовки приложений. */
    @Test
    void noIssues_whenAppendixEntriesOnlyInTocBlock() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("СОДЕРЖАНИЕ")
                .pageIndex(1)
                .pageEndIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ А\t14")
                .pageIndex(1)
                .pageEndIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ Б\t15")
                .pageIndex(1)
                .pageEndIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ПРИЛОЖЕНИЕ В\t16")
                .pageIndex(1)
                .pageEndIndex(1)
                .alignment("LEFT")
                .build());
        assertTrue(Ft15AppendixChecker.check(paras).isEmpty());
    }

    @Test
    void noIssues_whenTocStyleParagraphSkipped() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder()
                        .text("ПРИЛОЖЕНИЕ А")
                        .styleId("TOC1")
                        .pageIndex(1)
                        .pageEndIndex(1)
                        .alignment("LEFT")
                        .build()
        );
        assertTrue(Ft15AppendixChecker.check(paras).isEmpty());
    }
}
