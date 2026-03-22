package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParagraphInfo {
    private String text;

    /** Идентификатор стиля абзаца Word ({@code w:pStyle}), например Heading1. */
    private String styleId;

    /** Локализованное имя стиля из styles.xml (если есть). */
    private String styleName;

    /**
     * Уровень структуры (0 = заголовок 1-го уровня …). Нужен для ФТ-4–ФТ-6, ФТ-11.
     * Берётся из {@code w:outlineLvl} абзаца или цепочки стилей.
     */
    private Integer outlineLevel;

    /**
     * Все прописные (caps) в оформлении шрифта — для вариантов заголовков ФТ-11.
     */
    private Boolean caps;

    /**
     * Малые прописные (smallCaps).
     */
    private Boolean smallCaps;

    private String fontName;
    private Double fontSizePt;
    private Boolean bold;
    private Boolean italic;
    private String colorHex;
    private String alignment;
    private Double lineSpacing;
    private Double firstLineIndentCm;
    private Double leftIndentCm;
    /** 1-based: страница, с которой начинается абзац (оценка по разрывам OOXML). */
    private Integer pageIndex;
    /** 1-based: последняя страница, на которой заканчивается абзац (учёт {@code lastRenderedPageBreak} внутри абзаца). */
    private Integer pageEndIndex;

    /** Абзац внутри ячейки таблицы (не основной текст для ФТ-8/ФТ-9). */
    private boolean inTable;

    /** Содержит формулу Office Math (oMath) — исключается из ФТ-8/ФТ-9. */
    private boolean containsFormula;

    /**
     * По прогонам ({@code w:r}): есть фрагмент с шрифтом не Times New Roman (в т.ч. прямое оформление поверх стиля).
     */
    private boolean runFontViolatesTnr;

    /** По прогонам: размер вне диапазона 12–14 pt. */
    private boolean runFontSizeViolates;

    /** По прогонам: цвет не чёрный (в т.ч. локальный цвет в {@code w:color}). */
    private boolean runColorViolatesBlack;

    /** Для отчёта ФТ-8: какие шрифты встретились помимо TNR (через запятую, не более нескольких). */
    private String ft8NonTnrFontsFound;

    /** Для отчёта ФТ-8: какие размеры в pt вне 12–14. */
    private String ft8NonCompliantSizesFound;

    /** Для отчёта ФТ-8: какие цвета (hex) не чёрные. */
    private String ft8NonBlackColorsFound;
}
