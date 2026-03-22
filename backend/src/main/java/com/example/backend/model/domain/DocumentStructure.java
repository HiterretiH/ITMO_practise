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
