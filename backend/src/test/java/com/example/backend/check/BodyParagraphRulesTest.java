package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyParagraphRulesTest {

    @Test
    void mainBody_true_forTypicalParagraph() {
        ParagraphInfo p =
                ParagraphInfo.builder()
                        .text("Обычный абзац основного текста достаточной длины.")
                        .outlineLevel(null)
                        .inTable(false)
                        .containsFormula(false)
                        .numberingListParagraph(false)
                        .build();
        assertTrue(BodyParagraphRules.isMainBodyTextForFormatting(p));
    }

    @Test
    void mainBody_false_whenOutlineHeading() {
        ParagraphInfo p =
                ParagraphInfo.builder()
                        .text("1. ЗАГОЛОВОК")
                        .outlineLevel(0)
                        .inTable(false)
                        .containsFormula(false)
                        .build();
        assertFalse(BodyParagraphRules.isMainBodyTextForFormatting(p));
    }

    @Test
    void mainBody_false_whenInTable() {
        ParagraphInfo p =
                ParagraphInfo.builder()
                        .text("Ячейка")
                        .outlineLevel(null)
                        .inTable(true)
                        .containsFormula(false)
                        .build();
        assertFalse(BodyParagraphRules.isMainBodyTextForFormatting(p));
    }

    @Test
    void mainBody_false_whenFigureCaption() {
        ParagraphInfo p =
                ParagraphInfo.builder()
                        .text("Рисунок 1 — Пример")
                        .outlineLevel(null)
                        .inTable(false)
                        .containsFormula(false)
                        .build();
        assertFalse(BodyParagraphRules.isMainBodyTextForFormatting(p));
    }

    @Test
    void looksLikeTocEntry_whenTabAndPageTail() {
        assertTrue(
                BodyParagraphRules.looksLikeTocEntryLine(
                        "Введение\t12"));
    }
}
