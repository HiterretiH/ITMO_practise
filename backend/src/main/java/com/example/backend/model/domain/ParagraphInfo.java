package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParagraphInfo {
    private String text;
    private String fontName;
    private Double fontSizePt;
    private Boolean bold;
    private Boolean italic;
    private String colorHex;
    private String alignment;
    private Double lineSpacing;
    private Double firstLineIndentCm;
    private Double leftIndentCm;
    private Integer pageIndex;
}
