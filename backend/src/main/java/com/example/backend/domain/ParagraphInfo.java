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

    private boolean containsFormula;

    private boolean ooxmlDiscretionaryHyphenMarks;

    private boolean runFontViolatesTnr;

    private boolean runFontSizeViolates;

    private boolean runColorViolatesBlack;

    private String ft8NonTnrFontsFound;

    private String ft8NonCompliantSizesFound;

    private String ft8NonBlackColorsFound;
}
