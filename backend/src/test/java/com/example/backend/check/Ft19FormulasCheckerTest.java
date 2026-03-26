package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft19FormulasCheckerTest {

    @Test
    void extractNumber_afterTab() {
        assertEquals("1", Ft19FormulasChecker.extractFormulaNumber("E\t(1)"));
        assertEquals("2.3", Ft19FormulasChecker.extractFormulaNumber("a+b\t(2.3)"));
    }

    @Test
    void noIssues_whenNoFormulas() {
        List<ParagraphInfo> paras = List.of(
                ParagraphInfo.builder().text("Текст").build());
        assertTrue(Ft19FormulasChecker.check(paras, "Текст").isEmpty());
    }

    @Test
    void issue_whenNumberButNoReferenceInBody() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("Введение").build());
        paras.add(ParagraphInfo.builder().text("").build());
        paras.add(ParagraphInfo.builder().text("E\t(1)").containsFormula(true).build());
        paras.add(ParagraphInfo.builder().text("").build());
        paras.add(ParagraphInfo.builder().text("Конец").build());
        List<String> issues = Ft19FormulasChecker.check(paras, "Введение E Конец");
        assertEquals(1, issues.stream().filter(s -> s.contains("ссылк")).count());
    }

    @Test
    void noIssue_whenReferenceOutsideFormulaParagraph() {
        List<ParagraphInfo> paras = new ArrayList<>();
        paras.add(ParagraphInfo.builder().text("См. (1) далее").build());
        paras.add(ParagraphInfo.builder().text("").build());
        paras.add(ParagraphInfo.builder().text("E\t(1)").containsFormula(true).build());
        paras.add(ParagraphInfo.builder().text("").build());
        List<String> issues = Ft19FormulasChecker.check(paras, "См. (1) далее E");
        assertTrue(issues.stream().noneMatch(s -> s.contains("ссылк")));
    }

    @Test
    void issue_unitMixing() {
        String scan = "длина 10 мм и 10 mm";
        List<ParagraphInfo> paras = List.of(ParagraphInfo.builder().text(scan).build());
        List<String> issues = Ft19FormulasChecker.check(paras, scan);
        assertTrue(issues.stream().anyMatch(s -> s.contains("смешаны")));
    }

    @Test
    void issue_regularSpaceBeforeUnit() {
        String scan = "масса 5 кг";
        List<ParagraphInfo> paras = List.of(ParagraphInfo.builder().text(scan).build());
        List<String> issues = Ft19FormulasChecker.check(paras, scan);
        assertTrue(issues.stream().anyMatch(s -> s.contains("неразрывный")));
    }

    /** Номер раздела + название (оглавление) — не единица измерения. */
    @Test
    void noNbspFalsePositive_forTocOrSectionTitles() {
        String scan = "1 Контекст 6 Выводы 1 Определение 2 Яйца 52 Шлюшка";
        List<ParagraphInfo> paras = List.of(ParagraphInfo.builder().text(scan).build());
        List<String> issues = Ft19FormulasChecker.check(paras, scan);
        assertTrue(issues.stream().noneMatch(s -> s.contains("неразрывный")));
    }
}
