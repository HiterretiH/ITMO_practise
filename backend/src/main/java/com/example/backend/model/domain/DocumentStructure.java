package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DocumentStructure {
    @Builder.Default
    private List<ParagraphInfo> paragraphs = new ArrayList<>();

    private PageMargins margins;

    /** Поля, секции, размер страницы, нумерация (ФТ-3, ФТ-10, ФТ-12). */
    private DocumentPageSettings pageSettings;

    /**
     * Индексы абзацев (в общем списке {@link #paragraphs}), у которых в {@code w:pPr} задан {@code w:sectPr},
     * в порядке обхода тела документа; последняя секция может задаваться только {@code w:body/w:sectPr}.
     * Нужно для ФТ-10 (оценка диапазона страниц по секциям).
     */
    @Builder.Default
    private List<Integer> sectPrParagraphIndices = new ArrayList<>();

    @Builder.Default
    private List<TableInfo> tables = new ArrayList<>();

    @Builder.Default
    private List<FigureInfo> figures = new ArrayList<>();

    private String fullText;

    private String format;

    /** Каталог стилей документа (styles.xml), для ФТ-8–ФТ-11 и единообразия заголовков. */
    @Builder.Default
    private List<StyleDefinition> styleDefinitions = new ArrayList<>();
}
