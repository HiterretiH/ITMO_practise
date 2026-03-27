package com.example.backend.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParagraphInfo {
    private String text;

    private String styleId;

    private String styleName;

    private Integer outlineLevel;

    private Boolean caps;

    private Boolean smallCaps;

    private String fontName;
    private Double fontSizePt;
    private Boolean bold;

    private Boolean semiboldEmphasis;

    private Boolean italic;
    private String colorHex;
    private String alignment;
    private Double lineSpacing;
    private Double firstLineIndentCm;
    private Double leftIndentCm;
    private Integer pageIndex;
    private Integer pageEndIndex;

    private boolean inTable;

    /** 0-based номер строки таблицы Word (только при {@code inTable}). */
    private Integer tableRowIndex;

    /** 0-based номер столбца таблицы Word (только при {@code inTable}). */
    private Integer tableColumnIndex;

    /**
     * Абзац привязан к списку Word ({@code w:numPr}): маркированный или нумерованный список.
     * Номер/маркер часто не входят в {@link #text} — см. ФТ-20.
     */
    private boolean numberingListParagraph;

    /** Идентификатор экземпляра списка ({@code w:numId}), если задан. */
    private Integer numberingNumId;

    /** Уровень вложенности списка ({@code w:ilvl}), обычно 0 для основного уровня. */
    private Integer numberingIlvl;

    /**
     * Для абзаца со списком: {@code true}, если формат уровня — маркированный (bullet), а не нумерация.
     */
    private boolean numberingListBullet;

    /**
     * Для списка Word: значение {@code w:numFmt} уровня (decimal, bullet, lowerLetter, …). ФТ-21.
     */
    private String listNumberingFmt;

    /**
     * В абзаце есть Office Math (OMML): узлы {@code m:oMath} / {@code m:oMathPara} в XML абзаца.
     * См. {@link com.example.backend.util.OfficeMathDetector}.
     */
    private boolean containsFormula;

    private boolean ooxmlDiscretionaryHyphenMarks;

    private boolean runFontViolatesTnr;

    private boolean runFontSizeViolates;

    private boolean runColorViolatesBlack;

    private String ft8NonTnrFontsFound;

    private String ft8NonCompliantSizesFound;

    private String ft8NonBlackColorsFound;
}
