package com.example.backend;

import com.example.backend.check.Ft4RequiredSectionsChecker;
import com.example.backend.check.Ft5SectionNumberingChecker;
import com.example.backend.check.Ft6SectionStartChecker;
import com.example.backend.check.Ft7TocChecker;
import com.example.backend.check.Ft8MainFontChecker;
import com.example.backend.check.Ft9MainParagraphChecker;
import com.example.backend.check.Ft10PageMarginsChecker;
import com.example.backend.check.Ft11HeadingFormattingChecker;
import com.example.backend.check.Ft12PageNumberingChecker;
import com.example.backend.model.domain.DocumentPageSettings;
import com.example.backend.model.domain.DocumentStructure;
import com.example.backend.model.domain.FigureInfo;
import com.example.backend.model.domain.PageMargins;
import com.example.backend.model.domain.PageNumberingInfo;
import com.example.backend.model.domain.ParagraphInfo;
import com.example.backend.model.domain.SectionPageInfo;
import com.example.backend.model.domain.StyleDefinition;
import com.example.backend.model.domain.TableInfo;
import com.example.backend.service.DocxLoadService;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.ILoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DocxLoadDebugMain {

    private static final Logger log = LoggerFactory.getLogger(DocxLoadDebugMain.class);

    public static void main(String[] args) throws Exception {
        Path file = resolveDocPath(args);
        Path debugLogFile = resolveDebugLogFilePath(args, file);
        if (debugLogFile != null) {
            installUtf8FileLogAppender(debugLogFile);
        }

        String filename = file.getFileName().toString();
        String contentType = Files.probeContentType(file);

        DocxLoadService service = new DocxLoadService();
        DocumentStructure structure;
        try (InputStream is = Files.newInputStream(file)) {
            structure = service.load(filename, contentType, is);
        }

        if (debugLogFile != null) {
            log.info("Debug log (UTF-8): {}", debugLogFile.toAbsolutePath());
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

        List<StyleDefinition> styleDefs = structure.getStyleDefinitions();
        log.info("Style definitions (styles.xml), full list: {}", styleDefs == null ? 0 : styleDefs.size());
        if (styleDefs != null) {
            for (int i = 0; i < styleDefs.size(); i++) {
                StyleDefinition sd = styleDefs.get(i);
                log.info("  Style[{}]: id='{}', name='{}', type={}, basedOn='{}', outlineLvl={}",
                        i, sd.getStyleId(), sd.getName(), sd.getStyleType(), sd.getBasedOnStyleId(), sd.getOutlineLevel());
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
                    "Paragraph[{}]: pages={}-{}, styleId='{}', styleName='{}', outlineLvl={}, caps={}, smallCaps={}, inTable={}, formula={}, runTnrViol={}, runSzViol={}, runColViol={}, text='{}', fontName='{}', fontSizePt={}, bold={}, italic={}, colorHex='{}', alignment='{}', lineSpacing={}, firstLineIndentCm={}, leftIndentCm={}",
                    i,
                    p.getPageIndex(),
                    p.getPageEndIndex(),
                    p.getStyleId(),
                    p.getStyleName(),
                    p.getOutlineLevel(),
                    p.getCaps(),
                    p.getSmallCaps(),
                    p.isInTable(),
                    p.isContainsFormula(),
                    p.isRunFontViolatesTnr(),
                    p.isRunFontSizeViolates(),
                    p.isRunColorViolatesBlack(),
                    p.getText(),
                    p.getFontName(),
                    p.getFontSizePt(),
                    p.getBold(),
                    p.getItalic(),
                    p.getColorHex(),
                    p.getAlignment(),
                    p.getLineSpacing(),
                    p.getFirstLineIndentCm(),
                    p.getLeftIndentCm()
            );
        }

        List<String> ft4 = Ft4RequiredSectionsChecker.check(paragraphs);
        log.info("ФТ-4 (обязательные разделы, прописные заголовки), замечаний: {}", ft4.size());
        for (String line : ft4) {
            log.info("  {}", line);
        }

        List<String> ft5 = Ft5SectionNumberingChecker.check(paragraphs);
        log.info("ФТ-5 (нумерация глав и подразделов), замечаний: {}", ft5.size());
        for (String line : ft5) {
            log.info("  {}", line);
        }

        List<String> ft6 = Ft6SectionStartChecker.check(paragraphs);
        log.info("ФТ-6 (начало с новой страницы), найдено замечаний: {}", ft6.size());
        for (String line : ft6) {
            log.info("  {}", line);
        }
        if (ft6.isEmpty()) {
            log.info("  (замечаний по выбранным заголовкам нет — см. ограничения оценки страниц в PageLocator)");
        }

        List<String> ft7 = Ft7TocChecker.check(paragraphs);
        log.info("ФТ-7 (содержание: стили TOC, отточия, страницы, соответствие заголовкам), сообщений: {}", ft7.size());
        for (String line : ft7) {
            log.info("  {}", line);
        }

        List<String> ft8 = Ft8MainFontChecker.check(paragraphs);
        log.info("ФТ-8 (основной текст: шрифт Times New Roman, 12–14 pt, чёрный), замечаний: {}", ft8.size());
        for (String line : ft8) {
            log.info("  {}", line);
        }

        List<String> ft9 = Ft9MainParagraphChecker.check(paragraphs);
        log.info("ФТ-9 (основной текст: интервал 1,5; отступ 1,25 см; по ширине), замечаний: {}", ft9.size());
        for (String line : ft9) {
            log.info("  {}", line);
        }

        List<String> ft10 = Ft10PageMarginsChecker.check(
                pageSettings,
                margins,
                paragraphs,
                structure.getSectPrParagraphIndices());
        log.info("ФТ-10 (поля страницы: левое 30 мм; правое 10–15 мм; верх/низ по 20 мм, п. 4.2), замечаний: {}", ft10.size());
        for (String line : ft10) {
            log.info("  {}", line);
        }

        List<String> ft11 = Ft11HeadingFormattingChecker.check(paragraphs);
        log.info("ФТ-11 (заголовки разделов и подразделов: единый стиль глав, подразделы, точка, переносы, аббревиатуры, отступ; п. 4.4.4), сообщений: {}", ft11.size());
        for (String line : ft11) {
            log.info("  {}", line);
        }

        List<String> ft12 = Ft12PageNumberingChecker.check(pageSettings, paragraphs);
        log.info("ФТ-12 (п. 4.3.1: постоянная сквозная нумерация, внизу по центру, без точки после номера), замечаний: {}", ft12.size());
        for (String line : ft12) {
            log.info("  {}", line);
        }

        printParagraphStats(paragraphs);
        printHeadingCandidates(paragraphs);
        printPotentialIssues(paragraphs, tables, figures);
    }

    /**
     * Куда писать полный лог прогона: системное свойство {@code debug.output}, иначе второй аргумент,
     * иначе {@code output/right.txt} для «эталонных» входов и {@code output/wrong.txt}, если в имени файла есть {@code test2}.
     * Отключить: {@code -Ddebug.output=-} или {@code -Ddebug.output=false}.
     */
    private static Path resolveDebugLogFilePath(String[] args, Path docFile) {
        String prop = System.getProperty("debug.output");
        if ("-".equals(prop) || "false".equalsIgnoreCase(prop)) {
            return null;
        }
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop.trim()).toAbsolutePath().normalize();
        }
        if (args != null && args.length > 1 && args[1] != null && !args[1].isBlank()) {
            return Path.of(args[1].trim()).toAbsolutePath().normalize();
        }
        String name = docFile.getFileName().toString().toLowerCase(Locale.ROOT);
        Path rel = name.contains("test2") ? Path.of("output", "wrong.txt") : Path.of("output", "right.txt");
        return rel.toAbsolutePath().normalize();
    }

    /** Дублирование лога в файл в UTF-8 (консоль Gradle тоже с jvmArgs UTF-8 в build.gradle). */
    private static void installUtf8FileLogAppender(Path logFile) throws Exception {
        Files.createDirectories(logFile.getParent() != null ? logFile.getParent() : Path.of("."));
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext)) {
            return;
        }
        LoggerContext lc = (LoggerContext) factory;
        ch.qos.logback.classic.Logger root = lc.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        FileAppender<ILoggingEvent> fa = new FileAppender<>();
        fa.setName("DOCX_DEBUG_UTF8");
        fa.setContext(lc);
        fa.setFile(logFile.toString());
        fa.setAppend(false);
        PatternLayoutEncoder enc = new PatternLayoutEncoder();
        enc.setContext(lc);
        enc.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        enc.setCharset(StandardCharsets.UTF_8);
        enc.start();
        fa.setEncoder(enc);
        fa.start();
        root.addAppender(fa);
    }

    /**
     * Путь к .docx: первый аргумент командной строки (например {@code test-input/test2.docx}),
     * иначе по умолчанию {@code test-input/test.docx}.
     */
    private static Path resolveDocPath(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return resolveExistingPath(args[0].trim());
        }
        Path fromModule = Path.of("test-input", "test.docx");
        if (Files.exists(fromModule)) return fromModule;

        Path fromRoot = Path.of("backend", "test-input", "test.docx");
        if (Files.exists(fromRoot)) return fromRoot;

        throw new IllegalStateException("Cannot find test-input/test.docx (передайте путь к файлу первым аргументом)");
    }

    private static Path resolveExistingPath(String first) {
        Path p = Path.of(first);
        if (Files.exists(p)) {
            return p.toAbsolutePath().normalize();
        }
        Path underBackend = Path.of("backend", first);
        if (Files.exists(underBackend)) {
            return underBackend.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("Файл не найден: " + first + " (cwd должен быть каталогом backend или укажите абсолютный путь)");
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

