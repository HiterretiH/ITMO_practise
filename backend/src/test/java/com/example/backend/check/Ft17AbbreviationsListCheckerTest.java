package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import com.example.backend.domain.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft17AbbreviationsListCheckerTest {

    private static ParagraphInfo h0(String text) {
        return ParagraphInfo.builder().text(text).outlineLevel(0).alignment("LEFT").build();
    }

    @Test
    void diagnostics_sectionFound_whenHeadingEndsWithPeriod() {
        List<ParagraphInfo> paras = List.of(
                h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ."),
                ParagraphInfo.builder().text("БД — база").alignment("LEFT").build(),
                h0("ЗАКЛЮЧЕНИЕ"));
        String diag = Ft17AbbreviationsListChecker.formatSectionDiagnostics(paras, List.of());
        assertTrue(diag.contains("раздел найден"), diag);
        assertTrue(diag.contains("вариант 1"), diag);
    }

    @Test
    void diagnostics_sectionFound_whenOutlineLevelMissingButTitleMatches() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder()
                        .text("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ.")
                        .outlineLevel(null)
                        .alignment("LEFT")
                        .build(),
                ParagraphInfo.builder().text("БД — база").alignment("LEFT").build(),
                h0("ЗАКЛЮЧЕНИЕ"));
        String diag = Ft17AbbreviationsListChecker.formatSectionDiagnostics(paras, List.of());
        assertTrue(diag.contains("раздел найден"), diag);
    }

    @Test
    void noIssues_whenSectionAbsent() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("Введение").outlineLevel(0).build(),
                ParagraphInfo.builder().text("Текст").alignment("LEFT").build());
        assertTrue(Ft17AbbreviationsListChecker.check(paras, List.of()).isEmpty());
    }

    @Test
    void diagnostics_whenSectionAbsent() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("Введение").outlineLevel(0).build(),
                ParagraphInfo.builder().text("Текст").alignment("LEFT").build());
        assertTrue(Ft17AbbreviationsListChecker.formatSectionDiagnostics(paras, List.of()).contains("не найден"));
    }

    @Test
    void issue_whenSectionEmpty() {
        List<ParagraphInfo> paras = List.of(
                h0("ВВЕДЕНИЕ"),
                h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"),
                h0("ЗАКЛЮЧЕНИЕ"));
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("пуст"));
    }

    @Test
    void issue_whenMixedParagraphsAndTable() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"));
        paras.add(ParagraphInfo.builder().text("БД — база").alignment("LEFT").inTable(false).build());
        paras.add(ParagraphInfo.builder().text("cell").inTable(true).alignment("LEFT").build());
        paras.add(h0("ДАЛЕЕ"));
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("смешение"));
    }

    @Test
    void noIssues_variant1_valid() {
        List<ParagraphInfo> paras = List.of(
                h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"),
                ParagraphInfo.builder().text("БД — база данных").alignment("LEFT").pageIndex(2).build(),
                h0("ЗАКЛЮЧЕНИЕ"));
        assertTrue(Ft17AbbreviationsListChecker.check(paras, List.of()).isEmpty());
    }

    @Test
    void issue_variant1_noDash() {
        List<ParagraphInfo> paras = List.of(
                h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"),
                ParagraphInfo.builder().text("БД база данных").alignment("LEFT").build(),
                h0("ЗАКЛЮЧЕНИЕ"));
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("тире"));
    }

    @Test
    void issue_variant1_trailingPeriod() {
        List<ParagraphInfo> paras = List.of(
                h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"),
                ParagraphInfo.builder().text("БД — база данных.").alignment("LEFT").build(),
                h0("ЗАКЛЮЧЕНИЕ"));
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("препинания"));
    }

    @Test
    void issue_variant1_termNotCapitalized() {
        List<ParagraphInfo> paras = List.of(
                h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"),
                ParagraphInfo.builder().text("бд — база данных").alignment("LEFT").build(),
                h0("ЗАКЛЮЧЕНИЕ"));
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("прописной"));
    }

    @Test
    void issue_variant1_notLeft() {
        List<ParagraphInfo> paras = List.of(
                h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"),
                ParagraphInfo.builder().text("БД — база").alignment("CENTER").pageIndex(1).build(),
                h0("ЗАКЛЮЧЕНИЕ"));
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("левому"));
    }

    @Test
    void noIssues_variant2_twoColumnTable() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"));
        paras.add(ParagraphInfo.builder()
                .text("БД")
                .inTable(true)
                .tableRowIndex(0)
                .tableColumnIndex(0)
                .alignment("LEFT")
                .pageIndex(3)
                .build());
        paras.add(ParagraphInfo.builder()
                .text("база данных")
                .inTable(true)
                .tableRowIndex(0)
                .tableColumnIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(h0("ТЕРМИНЫ"));
        TableInfo table = TableInfo.builder()
                .paragraphIndex(1)
                .paragraphIndexEndExclusive(3)
                .pageIndex(3)
                .columnCount(2)
                .rowCount(1)
                .build();
        assertTrue(Ft17AbbreviationsListChecker.check(paras, List.of(table)).isEmpty());
    }

    @Test
    void issue_variant2_trailingPunctuationInCell() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"));
        paras.add(ParagraphInfo.builder()
                .text("БД")
                .inTable(true)
                .tableRowIndex(0)
                .tableColumnIndex(0)
                .build());
        paras.add(ParagraphInfo.builder()
                .text("база данных.")
                .inTable(true)
                .tableRowIndex(0)
                .tableColumnIndex(1)
                .build());
        paras.add(h0("ТЕРМИНЫ"));
        TableInfo table = TableInfo.builder()
                .paragraphIndex(1)
                .paragraphIndexEndExclusive(3)
                .pageIndex(1)
                .columnCount(2)
                .rowCount(1)
                .build();
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of(table));
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("столбец 2"));
        assertTrue(issues.get(0).contains("препинания"));
    }

    @Test
    void issue_variant2_emptyCell_secondRow() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"));
        paras.add(ParagraphInfo.builder().text("А").inTable(true).tableRowIndex(0).tableColumnIndex(0).build());
        paras.add(ParagraphInfo.builder().text("аа").inTable(true).tableRowIndex(0).tableColumnIndex(1).build());
        paras.add(ParagraphInfo.builder().text("Б").inTable(true).tableRowIndex(1).tableColumnIndex(0).build());
        paras.add(ParagraphInfo.builder().text("").inTable(true).tableRowIndex(1).tableColumnIndex(1).build());
        paras.add(h0("КОНЕЦ"));
        TableInfo table = TableInfo.builder()
                .paragraphIndex(1)
                .paragraphIndexEndExclusive(5)
                .pageIndex(1)
                .columnCount(2)
                .rowCount(2)
                .build();
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of(table));
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("строка 2"));
        assertTrue(issues.get(0).contains("пустые столбцы"));
        assertTrue(issues.get(0).contains("2"));
    }

    @Test
    void issue_variant2_oneColumnTable() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"));
        paras.add(ParagraphInfo.builder().text("x").inTable(true).build());
        paras.add(h0("ИТОГ"));
        TableInfo table = TableInfo.builder()
                .paragraphIndex(1)
                .paragraphIndexEndExclusive(2)
                .pageIndex(1)
                .columnCount(1)
                .rowCount(1)
                .build();
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of(table));
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("два столбца"));
    }

    @Test
    void issue_variant2_tableNotInSectionRange() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(h0("СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ"));
        paras.add(ParagraphInfo.builder().text("c1").inTable(true).tableRowIndex(0).tableColumnIndex(0).build());
        paras.add(ParagraphInfo.builder().text("c2").inTable(true).tableRowIndex(0).tableColumnIndex(1).build());
        paras.add(h0("ДРУГОЙ РАЗДЕЛ"));
        // Таблица объявлена с индексами до заголовка раздела — не пересекает (h+1..next-1)
        TableInfo table = TableInfo.builder()
                .paragraphIndex(0)
                .paragraphIndexEndExclusive(1)
                .pageIndex(1)
                .columnCount(2)
                .rowCount(1)
                .build();
        List<String> issues = Ft17AbbreviationsListChecker.check(paras, List.of(table));
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("не найдена таблица"));
    }
}
