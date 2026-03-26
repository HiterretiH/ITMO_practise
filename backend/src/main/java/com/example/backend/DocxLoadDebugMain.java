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
import com.example.backend.check.Ft13FigureCaptionChecker;
import com.example.backend.check.Ft14TableCaptionChecker;
import com.example.backend.check.Ft15AppendixChecker;
import com.example.backend.check.Ft16OptionalStructuralElementsChecker;
import com.example.backend.check.Ft17AbbreviationsListChecker;
import com.example.backend.check.Ft19FormulasChecker;
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

        List<String> ft12 = Ft12PageNumberingChecker.check(
                pageSettings, paragraphs, structure.getSectPrParagraphIndices());
        log.info("ФТ-12 (п. 4.3.1: постоянная сквозная нумерация, внизу по центру, без точки после номера), замечаний: {}", ft12.size());
        for (String line : ft12) {
            log.info("  {}", line);
        }

        List<String> ft13 = Ft13FigureCaptionChecker.check(figures, paragraphs);
        log.info("ФТ-13 (п. 4.5.3: подпись «Рисунок N – …», под рисунком, по центру), замечаний: {}", ft13.size());
        for (String line : ft13) {
            log.info("  {}", line);
        }

        List<String> ft14 = Ft14TableCaptionChecker.check(tables, paragraphs);
        log.info("ФТ-14 (п. 4.6.3: название «Таблица N – …» над таблицей слева; настоящая таблица Word), замечаний: {}", ft14.size());
        for (String line : ft14) {
            log.info("  {}", line);
        }

        List<String> ft15 = Ft15AppendixChecker.check(paragraphs);
        log.info("ФТ-15 (п. 4.11.2: приложения; «ПРИЛОЖЕНИЕ А» наверху страницы, по центру), замечаний: {}", ft15.size());
        for (String line : ft15) {
            log.info("  {}", line);
        }

        List<String> ft16 = Ft16OptionalStructuralElementsChecker.check(paragraphs);
        log.info("ФТ-16 (п. 3.2: доп. разделы по содержанию — список сокращений, термины, иллюстративный материал), замечаний: {}", ft16.size());
        for (String line : ft16) {
            log.info("  {}", line);
        }

        log.info("{}", Ft17AbbreviationsListChecker.formatSectionDiagnostics(paragraphs, tables));
        List<String> ft17 = Ft17AbbreviationsListChecker.check(paragraphs, tables);
        log.info("ФТ-17 (п. 4.8.4: список сокращений — один из двух вариантов: абзацы или таблица 2×N), замечаний: {}", ft17.size());
        for (String line : ft17) {
            log.info("  {}", line);
        }

        List<String> ft19 = Ft19FormulasChecker.check(paragraphs, structure.getFullText());
        log.info("ФТ-19 (п. 4.7: формулы — ссылки, где, единицы, неразрывный пробел), замечаний: {}", ft19.size());
        for (String line : ft19) {
            log.info("  {}", line);
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

