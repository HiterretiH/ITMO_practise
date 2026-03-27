package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft20BibliographyCheckerTest {

    @Test
    void issue_whenNoBibliographyHeading() {
        List<ParagraphInfo> paras = List.of(ParagraphInfo.builder().text("Текст [1]").build());
        List<String> issues = Ft20BibliographyChecker.check(paras, "Текст [1]");
        assertTrue(issues.stream().anyMatch(s -> s.contains("не найден заголовок")));
    }

    @Test
    void noIssues_whenWordNumberedListWithoutExplicitPrefixInText() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("В тексте [1] и [2].").build());
        paras.add(ParagraphInfo.builder().text("СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ").outlineLevel(0).build());
        paras.add(
                ParagraphInfo.builder()
                        .text("Иванов И.И. Книга. 2020.")
                        .numberingListParagraph(true)
                        .numberingNumId(1)
                        .numberingIlvl(0)
                        .numberingListBullet(false)
                        .build());
        paras.add(
                ParagraphInfo.builder()
                        .text("Петров П.П. Статья. 2021.")
                        .numberingListParagraph(true)
                        .numberingNumId(1)
                        .numberingIlvl(0)
                        .numberingListBullet(false)
                        .build());
        String fullText = "В тексте [1] и [2].\nСПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ\n";
        List<String> issues = Ft20BibliographyChecker.check(paras, fullText);
        assertTrue(issues.isEmpty());
    }

    @Test
    void noIssues_whenHeadingAndListMatchCitations() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("В тексте [1] и [2].").build());
        paras.add(ParagraphInfo.builder().text("СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ").outlineLevel(0).build());
        paras.add(ParagraphInfo.builder().text("1. Иванов И.И. Книга. 2020.").build());
        paras.add(ParagraphInfo.builder().text("2. Петров П.П. Статья. 2021.").build());
        String fullText = "В тексте [1] и [2].\nСПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ\n1. ...\n2. ...";
        List<String> issues = Ft20BibliographyChecker.check(paras, fullText);
        assertTrue(issues.isEmpty());
    }

    @Test
    void issue_whenListHasTwoSourcesButOnlyCitation2InFullDocument() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("В работе использована только ссылка [2].").build());
        paras.add(ParagraphInfo.builder().text("СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ").outlineLevel(0).build());
        paras.add(ParagraphInfo.builder().text("1. Иванов И.И. Книга.").build());
        paras.add(ParagraphInfo.builder().text("2. Петров П.П. Статья.").build());
        String fullText = "В работе использована только ссылка [2].\nСПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ\n1. ...\n2. ...";
        List<String> issues = Ft20BibliographyChecker.check(paras, fullText);
        assertTrue(issues.stream().anyMatch(s -> s.contains("источник №1") && s.contains("[1]")));
    }

    @Test
    void issue_whenCitationWithoutListEntry() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("Упоминание [2].").build());
        paras.add(ParagraphInfo.builder().text("СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ").outlineLevel(0).build());
        paras.add(ParagraphInfo.builder().text("1. Автор. Название.").build());
        String fullText = "Упоминание [2].\nСПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ\n1. ...";
        List<String> issues = Ft20BibliographyChecker.check(paras, fullText);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("[2]")));
    }
}
