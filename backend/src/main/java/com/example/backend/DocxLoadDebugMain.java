package com.example.backend;

import com.example.backend.check.runner.CheckExecutionResult;
import com.example.backend.check.runner.VkrChecksRunner;
import com.example.backend.config.checks.ChecksConfigurationLoader;
import com.example.backend.config.checks.ChecksConfigRoot;
import com.example.backend.domain.DocumentPageSettings;
import com.example.backend.domain.DocumentStructure;
import com.example.backend.domain.FigureInfo;
import com.example.backend.domain.PageMargins;
import com.example.backend.domain.PageNumberingInfo;
import com.example.backend.domain.ParagraphInfo;
import com.example.backend.domain.SectionPageInfo;
import com.example.backend.domain.StyleDefinition;
import com.example.backend.domain.TableInfo;
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
import java.util.stream.IntStream;

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
                if (num.getFooterPartCombinedTexts() != null && !num.getFooterPartCombinedTexts().isEmpty()) {
                    log.info("  footer part text (POI getText; не список «по каждой странице», а шаблон(ы) подвала):");
                    for (int i = 0; i < num.getFooterPartCombinedTexts().size(); i++) {
                        log.info("    [{}] {}", i, num.getFooterPartCombinedTexts().get(i));
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
            log.info("Table[{}]: caption='{}', paragraphIndex={}, captionParagraphIndex={}, pageIndex={}",
                    i, t.getCaption(), t.getParagraphIndex(), t.getCaptionParagraphIndex(), t.getPageIndex());
        }

        List<FigureInfo> figures = structure.getFigures();
        log.info("Figures count: {}", figures.size());
        for (int i = 0; i < figures.size(); i++) {
            FigureInfo f = figures.get(i);
            log.info("Figure[{}]: caption='{}', paragraphIndex={}, captionParagraphIndex={}, pageIndex={}",
                    i, f.getCaption(), f.getParagraphIndex(), f.getCaptionParagraphIndex(), f.getPageIndex());
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

        ChecksConfigRoot checksConfig = ChecksConfigurationLoader.loadClasspath("checks-config.json");
        List<CheckExecutionResult> checkResults = VkrChecksRunner.run(structure, checksConfig, log::info);
        for (CheckExecutionResult r : checkResults) {
            if (!r.ran()) {
                log.info("ФТ {} — {}: отключено в checks-config.json", r.id(), r.title());
                continue;
            }
            log.info("ФТ {} — {}, замечаний: {}", r.id(), r.title(), r.issues().size());
            for (String line : r.issues()) {
                log.info("  {}", line);
            }
            if ("ft6".equals(r.id()) && r.issues().isEmpty()) {
                log.info("  (замечаний по выбранным заголовкам нет — см. ограничения оценки страниц в PageLocator)");
            }
        }

        printPageEstimateAndAlignmentDiagnostic(paragraphs, pageSettings);

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

    /**
     * В лог (и в output/wrong.txt при файловом аппендере): сводка по оценке номеров страниц из OOXML
     * и выравниванию абзацев по порядку — чтобы сравнить с тем, что даёт w:pgNumType и поле PAGE в подвале.
     */
    private static void printPageEstimateAndAlignmentDiagnostic(
            List<ParagraphInfo> paragraphs,
            DocumentPageSettings pageSettings) {
        log.info("=== DIAG: оценка страниц (PageLocator) и выравнивание абзацев по порядку ===");
        log.info("DIAG: в файле .docx нет таблицы «номер на физ. стр.1, стр.2, …»; в подвале — шаблон(ы) с полем PAGE, "
                + "Word подставляет номер при вёрстке. Ряд чисел из «Содержания» в теле документа — это не подвал (см. ФТ-7).");
        PageNumberingInfo pnDiag = pageSettings != null ? pageSettings.getNumbering() : null;
        if (pnDiag != null && pnDiag.getFooterPartCombinedTexts() != null && !pnDiag.getFooterPartCombinedTexts().isEmpty()) {
            log.info("DIAG: текст подвалов как отдаёт POI (иногда кеш поля; не «по страницам»):");
            for (int i = 0; i < pnDiag.getFooterPartCombinedTexts().size(); i++) {
                log.info("DIAG:   footer[{}] «{}»", i, pnDiag.getFooterPartCombinedTexts().get(i));
            }
        }
        if (paragraphs == null || paragraphs.isEmpty()) {
            log.info("DIAG: нет абзацев");
            log.info("=== DIAG end ===");
            return;
        }
        int minP = Integer.MAX_VALUE;
        int maxP = 0;
        StringBuilder pageIndexOnly = new StringBuilder();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            Integer pi = p.getPageIndex();
            Integer pe = p.getPageEndIndex();
            if (pi != null) {
                minP = Math.min(minP, pi);
                maxP = Math.max(maxP, pi);
            }
            if (pe != null) {
                maxP = Math.max(maxP, pe);
            }
            if (pageIndexOnly.length() > 0) {
                pageIndexOnly.append(',');
            }
            pageIndexOnly.append(pi == null ? "?" : pi.toString());
        }
        if (minP == Integer.MAX_VALUE) {
            minP = 0;
        }
        log.info("DIAG: по абзацам minPage={} maxPage={} (pageIndex/pageEndIndex)", minP, maxP);
        log.info("DIAG: последовательность pageIndex по порядку абзацев #0..#{}: {}", paragraphs.size() - 1, pageIndexOnly);

        final int maxChunk = 3800;
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            String pi = p.getPageIndex() == null ? "?" : String.valueOf(p.getPageIndex());
            String pe = p.getPageEndIndex() == null ? "?" : String.valueOf(p.getPageEndIndex());
            String al = p.getAlignment() == null ? "null" : p.getAlignment();
            String part = String.format(Locale.ROOT, "#%d:%s-%s|%s; ", i, pi, pe, al);
            if (chunk.length() + part.length() > maxChunk) {
                log.info("DIAG: абзацы стр./выравнивание (фрагмент): {}", chunk);
                chunk = new StringBuilder(part);
            } else {
                chunk.append(part);
            }
        }
        if (chunk.length() > 0) {
            log.info("DIAG: абзацы стр./выравнивание (фрагмент): {}", chunk);
        }

        if (pageSettings != null && pageSettings.getSections() != null && !pageSettings.getSections().isEmpty() && maxP >= 1) {
            SectionPageInfo s0 = pageSettings.getSections().get(0);
            Integer wStart = s0.getPageNumberStart();
            if (wStart != null) {
                String fromWStart = IntStream.rangeClosed(1, maxP)
                        .map(pg -> wStart + pg - 1)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(","));
                String wantOneToN = IntStream.rangeClosed(1, maxP)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(","));
                log.info("DIAG: w:start первой секции = {} → поле PAGE на физ. стр. 1..{} даст номера: {}", wStart, maxP, fromWStart);
                log.info("DIAG: при сквозной нумерации 1..N на тех же страницах в подвале должны быть: {}", wantOneToN);
            } else {
                log.info("DIAG: w:start в первой секции не задан (null) — старт номера страницы по умолчанию обычно 1");
            }
        }
        log.info("=== DIAG end ===");
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

