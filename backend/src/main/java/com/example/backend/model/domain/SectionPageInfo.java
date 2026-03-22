package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Параметры страницы для одной секции документа (OOXML: {@code w:sectPr}).
 * Следует за абзацем с разрывом секции или задаёт последнюю секцию в {@code w:body}.
 */
@Data
@Builder
public class SectionPageInfo {
    /** Порядковый номер записи секции (0 — первая извлечённая секция). */
    private int sectionIndex;

    private PageMargins margins;

    /** Ширина страницы в twips (1/20 pt), если задана {@code w:pgSz}. */
    private Long pageWidthTwips;
    /** Высота страницы в twips, если задана {@code w:pgSz}. */
    private Long pageHeightTwips;

    /** true — альбомная ориентация ({@code w:orient="landscape"}). */
    private Boolean landscape;

    /**
     * Начало нумерации страниц в секции ({@code w:pgNumType w:start}), если задано.
     */
    private Integer pageNumberStart;

    /**
     * Формат номера страницы ({@code w:pgNumType w:fmt}), например decimal, upperRoman.
     */
    private String pageNumberFormat;

    /**
     * true — в {@code w:pgNumType} задан атрибут {@code w:start} (явный старт нумерации в секции).
     * Сам факт наличия {@code w:pgNumType} с форматом decimal у разрывов секции в Word не означает перезапуск.
     */
    private Boolean sectionRestartsPageNumbering;
}
