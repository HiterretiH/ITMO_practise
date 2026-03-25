package com.example.backend.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SectionPageInfo {
    private int sectionIndex;

    private PageMargins margins;

    private Long pageWidthTwips;

    private Long pageHeightTwips;

    private Boolean landscape;

    private Integer pageNumberStart;

    private String pageNumberFormat;

    private Boolean sectionRestartsPageNumbering;
}
