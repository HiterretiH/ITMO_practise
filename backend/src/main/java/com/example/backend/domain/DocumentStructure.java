package com.example.backend.domain;

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

    private DocumentPageSettings pageSettings;

    @Builder.Default
    private List<Integer> sectPrParagraphIndices = new ArrayList<>();

    @Builder.Default
    private List<TableInfo> tables = new ArrayList<>();

    @Builder.Default
    private List<FigureInfo> figures = new ArrayList<>();

    private String fullText;

    private String format;

    @Builder.Default
    private List<StyleDefinition> styleDefinitions = new ArrayList<>();
}
