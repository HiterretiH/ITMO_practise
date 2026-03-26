package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft16OptionalStructuralElementsCheckerTest {

    @Test
    void noIssues_whenTocListsTerminologyAndBodyHasHeading() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("СОДЕРЖАНИЕ")
                .pageIndex(1)
                .outlineLevel(0)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ\t9")
                .pageIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("Обычный абзац после оглавления.")
                .pageIndex(1)
                .alignment("BOTH")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ")
                .pageIndex(3)
                .outlineLevel(0)
                .alignment("LEFT")
                .build());

        assertTrue(Ft16OptionalStructuralElementsChecker.check(paras).isEmpty());
    }

    @Test
    void issue_whenTocListsTerminologyButNoBodyHeading() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("СОДЕРЖАНИЕ")
                .pageIndex(1)
                .outlineLevel(0)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ\t9")
                .pageIndex(1)
                .alignment("LEFT")
                .build());
        paras.add(ParagraphInfo.builder()
                .text("Дальше идёт текст без заголовка раздела.")
                .pageIndex(2)
                .alignment("BOTH")
                .build());

        List<String> issues = Ft16OptionalStructuralElementsChecker.check(paras);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("ФТ-16") && s.contains("ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ")));
    }

    @Test
    void noIssues_whenTocDoesNotListOptionalSection() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder()
                .text("СОДЕРЖАНИЕ")
                .pageIndex(1)
                .outlineLevel(0)
                .build());
        paras.add(ParagraphInfo.builder()
                .text("ВВЕДЕНИЕ\t10")
                .pageIndex(1)
                .build());
        paras.add(ParagraphInfo.builder()
                .text("Текст.")
                .pageIndex(2)
                .build());

        assertTrue(Ft16OptionalStructuralElementsChecker.check(paras).isEmpty());
    }
}
