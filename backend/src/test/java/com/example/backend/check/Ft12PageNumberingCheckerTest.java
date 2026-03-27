package com.example.backend.check;

import com.example.backend.domain.DocumentPageSettings;
import com.example.backend.domain.PageNumberingInfo;
import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft12PageNumberingCheckerTest {

    @Test
    void issue_whenPageSettingsNull() {
        List<String> issues = Ft12PageNumberingChecker.check(null, List.of(), List.of());
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("ФТ-12"));
    }

    @Test
    void issue_whenNumberingNull() {
        DocumentPageSettings settings = DocumentPageSettings.builder().build();
        List<String> issues = Ft12PageNumberingChecker.check(settings, List.of(), List.of());
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("нет сведений о нумерации")));
    }

    @Test
    void issue_whenNoFooterPageField() {
        PageNumberingInfo num =
                PageNumberingInfo.builder()
                        .footerPageFieldPresent(false)
                        .headerPageFieldPresent(false)
                        .build();
        DocumentPageSettings settings = DocumentPageSettings.builder().numbering(num).build();
        List<String> issues =
                Ft12PageNumberingChecker.check(
                        settings, List.of(ParagraphInfo.builder().pageIndex(1).build()), List.of());
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("нижнем колонтитуле") || s.contains("PAGE")));
    }

    @Test
    void fewerIssues_whenFooterPagePresentAndCentered() {
        PageNumberingInfo num =
                PageNumberingInfo.builder()
                        .footerPageFieldPresent(true)
                        .footerPageParagraphCentered(true)
                        .footerTrailingPeriodAfterPageSuspected(false)
                        .pageNumberRestartInSections(false)
                        .firstPageFooterPresent(false)
                        .evenPageFooterPresent(false)
                        .build();
        DocumentPageSettings settings = DocumentPageSettings.builder().numbering(num).build();
        List<String> issues =
                Ft12PageNumberingChecker.check(
                        settings, List.of(ParagraphInfo.builder().pageIndex(1).build()), List.of());
        assertTrue(issues.stream().noneMatch(s -> s.contains("не найдено поле PAGE")));
    }
}
