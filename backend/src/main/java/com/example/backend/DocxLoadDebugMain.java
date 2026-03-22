package com.example.backend;

import com.example.backend.model.domain.DocumentPageSettings;
import com.example.backend.model.domain.DocumentStructure;
import com.example.backend.model.domain.FigureInfo;
import com.example.backend.model.domain.PageMargins;
import com.example.backend.model.domain.PageNumberingInfo;
import com.example.backend.model.domain.ParagraphInfo;
import com.example.backend.model.domain.SectionPageInfo;
import com.example.backend.model.domain.TableInfo;
import com.example.backend.service.DocxLoadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DocxLoadDebugMain {

    private static final Logger log = LoggerFactory.getLogger(DocxLoadDebugMain.class);

    public static void main(String[] args) throws Exception {
        Path file = resolveDocPath();
        String filename = file.getFileName().toString();
        String contentType = Files.probeContentType(file);

        DocxLoadService service = new DocxLoadService();
        DocumentStructure structure;
        try (InputStream is = Files.newInputStream(file)) {
            structure = service.load(filename, contentType, is);
        }

        log.info("File: {}", file.toAbsolutePath());
        log.info("Format: {}", structure.getFormat());
        log.info("Full text length: {}", structure.getFullText() == null ? 0 : structure.getFullText().length());
        log.info("Detected contentType: {}", contentType);
        log.info("Full text preview:\n{}", preview(structure.getFullText(), 1200));

        PageMargins margins = structure.getMargins();
        if (margins == null) {
            log.info("Margins: null");
        } else {
            log.info("Margins: leftCm={}, rightCm={}, topCm={}, bottomCm={}",
                    margins.getLeftCm(), margins.getRightCm(), margins.getTopCm(), margins.getBottomCm());
        }

        DocumentPageSettings pageSettings = structure.getPageSettings();
        if (pageSettings != null) {
            List<SectionPageInfo> sections = pageSettings.getSections();
            log.info("Page sections (sectPr): {}", sections.size());
            for (int i = 0; i < sections.size(); i++) {
                SectionPageInfo s = sections.get(i);
                log.info(
                        "  Section[{}]: margins={}, pageWxH_twips={}x{}, landscape={}, pgStart={}, pgFmt={}, restartPgNum={}",
                        i,
                        s.getMargins(),
                        s.getPageWidthTwips(),
                        s.getPageHeightTwips(),
                        s.getLandscape(),
                        s.getPageNumberStart(),
                        s.getPageNumberFormat(),
                        s.getSectionRestartsPageNumbering());
            }
            PageNumberingInfo num = pageSettings.getNumbering();
            if (num != null) {
                log.info(
                        "Page numbering: footerPAGE={}, headerPAGE={}, footers={}, headers={}, footerCentered={}, restartInSections={}",
                        num.isFooterPageFieldPresent(),
                        num.isHeaderPageFieldPresent(),
                        num.getFooterPartCount(),
                        num.getHeaderPartCount(),
                        num.getFooterPageParagraphCentered(),
                        num.isPageNumberRestartInSections());
                if (num.getFooterNotes() != null && !num.getFooterNotes().isEmpty()) {
                    for (String n : num.getFooterNotes()) {
                        log.info("  note: {}", n);
                    }
                }
            }
        }

        List<TableInfo> tables = structure.getTables();
        log.info("Tables count: {}", tables.size());
        for (int i = 0; i < tables.size(); i++) {
            TableInfo t = tables.get(i);
            log.info("Table[{}]: caption='{}', paragraphIndex={}, pageIndex={}",
                    i, t.getCaption(), t.getParagraphIndex(), t.getPageIndex());
        }

        List<FigureInfo> figures = structure.getFigures();
        log.info("Figures count: {}", figures.size());
        for (int i = 0; i < figures.size(); i++) {
            FigureInfo f = figures.get(i);
            log.info("Figure[{}]: caption='{}', paragraphIndex={}, pageIndex={}",
                    i, f.getCaption(), f.getParagraphIndex(), f.getPageIndex());
        }

        List<ParagraphInfo> paragraphs = structure.getParagraphs();
        log.info("Paragraphs count: {}", paragraphs.size());
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            log.info(
                    "Paragraph[{}]: text='{}', fontName='{}', fontSizePt={}, bold={}, italic={}, colorHex='{}', alignment='{}', lineSpacing={}, firstLineIndentCm={}, leftIndentCm={}, pageIndex={}",
                    i,
                    p.getText(),
                    p.getFontName(),
                    p.getFontSizePt(),
                    p.getBold(),
                    p.getItalic(),
                    p.getColorHex(),
                    p.getAlignment(),
                    p.getLineSpacing(),
                    p.getFirstLineIndentCm(),
                    p.getLeftIndentCm(),
                    p.getPageIndex()
            );
        }

        printParagraphStats(paragraphs);
        printHeadingCandidates(paragraphs);
        printPotentialIssues(paragraphs, tables, figures);
    }

    private static Path resolveDocPath() {
        Path fromModule = Path.of("test-input", "test.docx");
        if (Files.exists(fromModule)) return fromModule;

        Path fromRoot = Path.of("backend", "test-input", "test.docx");
        if (Files.exists(fromRoot)) return fromRoot;

        throw new IllegalStateException("Cannot find test-input/test.docx");
    }

    private static void printParagraphStats(List<ParagraphInfo> paragraphs) {
        Map<String, Long> fontStats = paragraphs.stream()
                .map(ParagraphInfo::getFontName)
                .map(v -> v == null ? "null" : v)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        Map<String, Long> sizeStats = paragraphs.stream()
                .map(ParagraphInfo::getFontSizePt)
                .map(v -> v == null ? "null" : String.format(Locale.ROOT, "%.2f", v))
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        Map<String, Long> alignStats = paragraphs.stream()
                .map(ParagraphInfo::getAlignment)
                .map(v -> v == null ? "null" : v)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        Map<String, Long> spacingStats = paragraphs.stream()
                .map(ParagraphInfo::getLineSpacing)
                .map(v -> v == null ? "null" : String.format(Locale.ROOT, "%.3f", v))
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        log.info("Font distribution: {}", sortMap(fontStats));
        log.info("Font size distribution: {}", sortMap(sizeStats));
        log.info("Alignment distribution: {}", sortMap(alignStats));
        log.info("Line spacing distribution: {}", sortMap(spacingStats));
    }

    private static void printHeadingCandidates(List<ParagraphInfo> paragraphs) {
        log.info("Heading candidates:");
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            String text = safeTrim(p.getText());
            if (text.isEmpty()) continue;
            if (isHeadingLike(text, p)) {
                log.info("Heading[{}]: text='{}', size={}, bold={}, alignment={}",
                        i, text, p.getFontSizePt(), p.getBold(), p.getAlignment());
            }
        }
    }

    private static void printPotentialIssues(List<ParagraphInfo> paragraphs, List<TableInfo> tables, List<FigureInfo> figures) {
        long nullFont = paragraphs.stream().filter(p -> p.getFontName() == null).count();
        long nullSize = paragraphs.stream().filter(p -> p.getFontSizePt() == null).count();
        long emptyText = paragraphs.stream().filter(p -> safeTrim(p.getText()).isEmpty()).count();
        long nullTableCaption = tables.stream().filter(t -> t.getCaption() == null || t.getCaption().isBlank()).count();
        long nullFigureCaption = figures.stream().filter(f -> f.getCaption() == null || f.getCaption().isBlank()).count();

        log.info("Potential issues summary:");
        log.info("Paragraphs with null fontName: {}", nullFont);
        log.info("Paragraphs with null fontSizePt: {}", nullSize);
        log.info("Paragraphs with empty text: {}", emptyText);
        log.info("Tables without caption: {}", nullTableCaption);
        log.info("Figures without caption: {}", nullFigureCaption);
    }

    private static boolean isHeadingLike(String text, ParagraphInfo p) {
        String normalized = text.trim();
        boolean uppercase = normalized.equals(normalized.toUpperCase(Locale.ROOT));
        boolean numbered = normalized.matches("^\\d+(\\.\\d+)*\\s+.*");
        boolean shortLine = normalized.length() <= 120 && !normalized.contains(".");
        boolean emphasized = Boolean.TRUE.equals(p.getBold()) || (p.getFontSizePt() != null && p.getFontSizePt() >= 16.0);
        return (uppercase || numbered || emphasized) && shortLine;
    }

    private static String preview(String text, int maxChars) {
        if (text == null) return "null";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n... [truncated]";
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> Map<T, Long> sortMap(Map<T, Long> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<T, Long>comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                ));
    }
}

