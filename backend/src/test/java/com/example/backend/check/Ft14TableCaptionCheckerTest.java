package com.example.backend.check;

import com.example.backend.model.domain.ParagraphInfo;
import com.example.backend.model.domain.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft14TableCaptionCheckerTest {

    @Test
    void noIssues_whenCaptionValidLeftAbove() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("Таблица 1 – Данные").alignment("LEFT").build());
        for (int i = 0; i < 3; i++) {
            paras.add(ParagraphInfo.builder().text("cell").inTable(true).alignment("LEFT").build());
        }
        TableInfo t = TableInfo.builder()
                .caption("Таблица 1 – Данные")
                .paragraphIndex(1)
                .pageIndex(2)
                .captionParagraphIndex(0)
                .build();
        assertTrue(Ft14TableCaptionChecker.check(List.of(t), paras).isEmpty());
    }

    @Test
    void nullAlignment_treatedAsOk_forLeft() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("Таблица 1 – X").alignment(null).build(),
                ParagraphInfo.builder().text("x").inTable(true).build()
        );
        TableInfo t = TableInfo.builder()
                .caption("Таблица 1 – X")
                .paragraphIndex(1)
                .pageIndex(1)
                .captionParagraphIndex(0)
                .build();
        assertTrue(Ft14TableCaptionChecker.check(List.of(t), paras).isEmpty());
    }

    @Test
    void issue_whenCaptionMissing() {
        TableInfo t = TableInfo.builder()
                .paragraphIndex(0)
                .pageIndex(1)
                .build();
        assertEquals(1, Ft14TableCaptionChecker.check(List.of(t), List.of()).size());
    }

    @Test
    void issue_whenNotLeft() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("Таблица 1 – А").alignment("CENTER").build(),
                ParagraphInfo.builder().text("x").inTable(true).build()
        );
        TableInfo t = TableInfo.builder()
                .caption("Таблица 1 – А")
                .paragraphIndex(1)
                .pageIndex(1)
                .captionParagraphIndex(0)
                .build();
        List<String> issues = Ft14TableCaptionChecker.check(List.of(t), paras);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("лев"));
    }

    @Test
    void issue_whenCaptionBelowTable() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("x").inTable(true).build());
        paras.add(ParagraphInfo.builder().text("Таблица 1 – А").alignment("LEFT").build());
        TableInfo t = TableInfo.builder()
                .caption("Таблица 1 – А")
                .paragraphIndex(0)
                .pageIndex(1)
                .captionParagraphIndex(1)
                .build();
        assertTrue(Ft14TableCaptionChecker.check(List.of(t), paras).stream().anyMatch(s -> s.contains("над таблицей")));
    }

    @Test
    void issue_whenWrongFormat() {
        TableInfo t = TableInfo.builder()
                .caption("Табл. 1")
                .paragraphIndex(5)
                .pageIndex(1)
                .captionParagraphIndex(4)
                .build();
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("x").build(),
                ParagraphInfo.builder().text("Табл. 1").alignment("LEFT").build()
        );
        assertFalse(Ft14TableCaptionChecker.check(List.of(t), paras).isEmpty());
    }
}
