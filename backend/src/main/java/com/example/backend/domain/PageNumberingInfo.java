package com.example.backend.domain;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class PageNumberingInfo {

    private boolean footerPageFieldPresent;

    private boolean headerPageFieldPresent;

    private int footerPartCount;

    private int headerPartCount;

    private Boolean footerPageParagraphCentered;

    private boolean pageNumberRestartInSections;

    private boolean footerTrailingPeriodAfterPageSuspected;

    private boolean defaultFooterHasPageField;

    private boolean firstPageFooterPresent;

    private boolean firstPageFooterHasPageField;

    private boolean evenPageFooterPresent;

    private boolean evenPageFooterHasPageField;

    @Builder.Default
    private List<String> footerNotes = new ArrayList<>();
}
