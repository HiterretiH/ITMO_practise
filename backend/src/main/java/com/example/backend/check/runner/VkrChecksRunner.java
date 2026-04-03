package com.example.backend.check.runner;

import com.example.backend.check.Ft10PageMarginsChecker;
import com.example.backend.check.Ft11HeadingFormattingChecker;
import com.example.backend.check.Ft12PageNumberingChecker;
import com.example.backend.check.Ft13FigureCaptionChecker;
import com.example.backend.check.Ft14TableCaptionChecker;
import com.example.backend.check.Ft15AppendixChecker;
import com.example.backend.check.Ft16OptionalStructuralElementsChecker;
import com.example.backend.check.Ft17AbbreviationsListChecker;
import com.example.backend.check.Ft18TermsDefinitionsChecker;
import com.example.backend.check.Ft19FormulasChecker;
import com.example.backend.check.Ft20BibliographyChecker;
import com.example.backend.check.Ft21ListsEnumerationChecker;
import com.example.backend.check.Ft4RequiredSectionsChecker;
import com.example.backend.check.Ft5SectionNumberingChecker;
import com.example.backend.check.Ft6SectionStartChecker;
import com.example.backend.check.Ft7TocChecker;
import com.example.backend.check.Ft8MainFontChecker;
import com.example.backend.check.Ft9MainParagraphChecker;
import com.example.backend.config.checks.CheckRuleDefinition;
import com.example.backend.config.checks.CheckSession;
import com.example.backend.config.checks.ChecksConfigRoot;
import com.example.backend.domain.DocumentStructure;
import com.example.backend.domain.FigureInfo;
import com.example.backend.domain.PageMargins;
import com.example.backend.domain.ParagraphInfo;
import com.example.backend.domain.TableInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Запуск набора ФТ в порядке, заданном в {@code checks-config.json}, с учётом {@link CheckRuleDefinition#enabled()}.
 */
public final class VkrChecksRunner {

    private static final Logger log = LoggerFactory.getLogger(VkrChecksRunner.class);

    private VkrChecksRunner() {
    }

    public static List<CheckExecutionResult> run(DocumentStructure structure, ChecksConfigRoot config) {
        return run(structure, config, null);
    }

    /**
     * @param diagnosticsSink необязательный вывод отладочных блоков (ФТ-17, 18, 20), если в правиле {@code logDiagnostics: true}
     */
    public static List<CheckExecutionResult> run(
            DocumentStructure structure, ChecksConfigRoot config, Consumer<String> diagnosticsSink) {
        if (structure == null || config == null || config.rules() == null) {
            return List.of();
        }
        List<ParagraphInfo> paragraphs =
                structure.getParagraphs() != null ? structure.getParagraphs() : List.of();
        List<TableInfo> tables = structure.getTables() != null ? structure.getTables() : List.of();
        List<FigureInfo> figures = structure.getFigures() != null ? structure.getFigures() : List.of();
        String fullText = structure.getFullText() != null ? structure.getFullText() : "";
        PageMargins margins = structure.getMargins();

        CheckSession.begin(config);
        try {
            List<CheckExecutionResult> out = new ArrayList<>();
            for (CheckRuleDefinition rule : config.rules()) {
                String id = rule.id() == null ? "" : rule.id().trim().toLowerCase(Locale.ROOT);
                String title = rule.title() != null && !rule.title().isBlank() ? rule.title() : id;
                if (!rule.enabled()) {
                    if (log.isDebugEnabled()) {
                        log.debug("check rule {} ({}) skipped (disabled)", id, title);
                    }
                    out.add(new CheckExecutionResult(id, title, false, List.of()));
                    continue;
                }
                long tRule = System.nanoTime();
                List<String> issues = runOne(id, rule, structure, paragraphs, tables, figures, fullText, margins, diagnosticsSink);
                long ruleMs = (System.nanoTime() - tRule) / 1_000_000L;
                if (log.isDebugEnabled()) {
                    log.debug("check rule {} ({}) done in {} ms (issues={})", id, title, ruleMs, issues.size());
                }
                out.add(new CheckExecutionResult(id, title, true, issues));
            }
            return Collections.unmodifiableList(out);
        } finally {
            CheckSession.end();
        }
    }

    private static List<String> runOne(
            String id,
            CheckRuleDefinition rule,
            DocumentStructure structure,
            List<ParagraphInfo> paragraphs,
            List<TableInfo> tables,
            List<FigureInfo> figures,
            String fullText,
            PageMargins margins,
            Consumer<String> diagnosticsSink) {
        return switch (id) {
            case "ft4" -> Ft4RequiredSectionsChecker.check(paragraphs);
            case "ft5" -> Ft5SectionNumberingChecker.check(paragraphs);
            case "ft6" -> Ft6SectionStartChecker.check(paragraphs);
            case "ft7" -> Ft7TocChecker.check(paragraphs);
            case "ft8" -> Ft8MainFontChecker.check(paragraphs);
            case "ft9" -> Ft9MainParagraphChecker.check(paragraphs);
            case "ft10" -> Ft10PageMarginsChecker.check(
                    structure.getPageSettings(),
                    margins,
                    paragraphs,
                    structure.getSectPrParagraphIndices());
            case "ft11" -> Ft11HeadingFormattingChecker.check(paragraphs);
            case "ft12" -> Ft12PageNumberingChecker.check(
                    structure.getPageSettings(), paragraphs, structure.getSectPrParagraphIndices());
            case "ft13" -> Ft13FigureCaptionChecker.check(figures, paragraphs);
            case "ft14" -> Ft14TableCaptionChecker.check(tables, paragraphs);
            case "ft15" -> Ft15AppendixChecker.check(paragraphs);
            case "ft16" -> Ft16OptionalStructuralElementsChecker.check(paragraphs);
            case "ft17" -> {
                if (rule.logDiagnosticsEffective() && diagnosticsSink != null) {
                    diagnosticsSink.accept(Ft17AbbreviationsListChecker.formatSectionDiagnostics(paragraphs, tables));
                }
                yield Ft17AbbreviationsListChecker.check(paragraphs, tables);
            }
            case "ft18" -> {
                if (rule.logDiagnosticsEffective() && diagnosticsSink != null) {
                    diagnosticsSink.accept(Ft18TermsDefinitionsChecker.formatSectionDiagnostics(paragraphs, tables));
                }
                yield Ft18TermsDefinitionsChecker.check(paragraphs, tables);
            }
            case "ft19" -> Ft19FormulasChecker.check(paragraphs);
            case "ft20" -> {
                if (rule.logDiagnosticsEffective() && diagnosticsSink != null) {
                    diagnosticsSink.accept(Ft20BibliographyChecker.formatSectionDiagnostics(paragraphs, fullText));
                    for (String line : Ft20BibliographyChecker.formatCitationMatrixLines(paragraphs, fullText)) {
                        diagnosticsSink.accept(line);
                    }
                }
                yield Ft20BibliographyChecker.check(paragraphs, fullText);
            }
            case "ft21" -> Ft21ListsEnumerationChecker.check(paragraphs);
            default -> throw new IllegalStateException("Неизвестное правило (должно отсекаться при загрузке checks-config): " + id);
        };
    }
}
