package com.example.backend.testsupport;

import com.example.backend.domain.ParagraphInfo;

/**
 * Минимальные абзацы для юнит-тестов чекеров (без загрузки .docx).
 */
public final class ParagraphFixtures {

    private ParagraphFixtures() {
    }

    /** Обычный абзац основного текста по правилам {@link com.example.backend.check.BodyParagraphRules}. */
    public static ParagraphInfo.ParagraphInfoBuilder bodyBuilder() {
        return ParagraphInfo.builder()
                .text("Основной текст абзаца достаточной длины для проверки форматирования.")
                .outlineLevel(null)
                .inTable(false)
                .containsFormula(false)
                .numberingListParagraph(false);
    }

    public static ParagraphInfo bodyTimes12Black() {
        return bodyBuilder()
                .fontName("Times New Roman")
                .fontSizePt(12.0)
                .colorHex("000000")
                .runFontViolatesTnr(false)
                .runFontSizeViolates(false)
                .runColorViolatesBlack(false)
                .lineSpacing(1.5)
                .firstLineIndentCm(1.25)
                .alignment("BOTH")
                .pageIndex(1)
                .build();
    }

    public static ParagraphInfo outlineHeading(int level, String text) {
        return ParagraphInfo.builder()
                .text(text)
                .outlineLevel(level)
                .inTable(false)
                .containsFormula(false)
                .pageIndex(1)
                .build();
    }
}
