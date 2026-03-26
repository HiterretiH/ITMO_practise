package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ФТ-7: соответствие оглавления тексту (п. 3.4), отточия, номера страниц.
 * <p>
 * <b>Стили Word TOC1 / TOC2 / TOC3</b> — уровни автоматического оглавления: первый уровень (главы),
 * второй (подразделы), третий. Если в документе они не применены к абзацам (поле оглавления, другой шаблон),
 * блок ищется по <b>виду строки</b>: текст, отточие, номер страницы в конце.
 * <p>
 * Заголовки «СОДЕРЖАНИЕ» и «ОГЛАВЛЕНИЕ» <strong>не сопоставляются</strong> с текстом работы; проверяется только,
 * что ровно одно из этих слов есть в документе как заголовок раздела оглавления (один раз).
 */
public final class Ft7TocChecker {

    private static final Set<String> TOC_SECTION_TITLES = Set.of(
            "СОДЕРЖАНИЕ",
            "ОГЛАВЛЕНИЕ"
    );

    private static final Pattern LINE_WITH_LEADER = Pattern.compile(
            "^(?<title>.+?)(?<leader>\\s*[\\.…\u2026·﹒\u00B7]{2,}|\\t+)\\s*(?<page>\\d{1,4})\\s*$",
            Pattern.UNICODE_CASE | Pattern.DOTALL
    );

    private static final Pattern LINE_TAIL_PAGE = Pattern.compile(
            "^(?<title>.+?)\\s+(?<page>\\d{1,4})\\s*$",
            Pattern.UNICODE_CASE | Pattern.DOTALL
    );

    private static final Pattern TOC_STYLE_ID = Pattern.compile("(?i)^TOC(\\d+)$");

    private Ft7TocChecker() {
    }

    /**
     * Индексы абзацев, входящих в блок оглавления по той же логике, что и {@link #check}.
     * Для ФТ-15 и др.: не считать такие абзацы заголовками приложений в основном тексте.
     */
    public static Set<Integer> indicesOfParagraphsInTocBlock(List<ParagraphInfo> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return Set.of();
        }
        List<TocParsedLine> byStyle = collectByTocStyles(paragraphs);
        List<TocParsedLine> byHeuristic = collectLongestContiguousHeuristic(paragraphs);

        if (byHeuristic.size() > byStyle.size() && !byHeuristic.isEmpty()) {
            return indicesFromHeuristicBounds(paragraphs);
        }
        if (!byStyle.isEmpty()) {
            return indicesFromStyleTocBlock(paragraphs);
        }
        if (!byHeuristic.isEmpty()) {
            return indicesFromHeuristicBounds(paragraphs);
        }
        return Set.of();
    }

    /**
     * Все абзацы оглавления для исключения из проверок «как в тексте»: объединение
     * {@link #indicesOfParagraphsInTocBlock} и непрерывного блока сразу после заголовка
     * «СОДЕРЖАНИЕ»/«ОГЛАВЛЕНИЕ» до первого структурного заголовка раздела (та же граница, что у поля оглавления в ФТ-7).
     * Последний охватывает строки вида «ПРИЛОЖЕНИЕ А» в содержании даже без номера страницы в том же абзаце.
     */
    public static Set<Integer> indicesOfParagraphsInTocSection(List<ParagraphInfo> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return Set.of();
        }
        Set<Integer> out = new HashSet<>(indicesOfParagraphsInTocBlock(paragraphs));
        out.addAll(indicesOfParagraphsInTocBodyAfterHeading(paragraphs));
        return out;
    }

    /**
     * Абзацы с индексами (tocHeadingIndex + 1) … до первого структурного заголовка раздела после оглавления
     * (не включая этот заголовок).
     */
    public static Set<Integer> indicesOfParagraphsInTocBodyAfterHeading(List<ParagraphInfo> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return Set.of();
        }
        int h = findTocHeadingIndex(paragraphs);
        if (h < 0) {
            return Set.of();
        }
        Set<Integer> out = new HashSet<>();
        for (int i = h + 1; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (shouldStopTocBlock(p)) {
                break;
            }
            out.add(i);
        }
        return out;
    }

    private static Set<Integer> indicesFromHeuristicBounds(List<ParagraphInfo> paragraphs) {
        int[] bounds = heuristicTocRunBounds(paragraphs);
        if (bounds == null) {
            return Set.of();
        }
        Set<Integer> s = new HashSet<>();
        for (int i = bounds[0]; i < bounds[1]; i++) {
            s.add(i);
        }
        return s;
    }

    private static Set<Integer> indicesFromStyleTocBlock(List<ParagraphInfo> paragraphs) {
        int tocHeadingIdx = findTocHeadingIndex(paragraphs);
        int lineStart;
        if (tocHeadingIdx >= 0) {
            lineStart = tocHeadingIdx + 1;
        } else {
            int ft = findFirstTocStyledParagraphIndex(paragraphs);
            if (ft < 0) {
                return Set.of();
            }
            lineStart = ft;
        }
        Set<Integer> s = new HashSet<>();
        for (int i = lineStart; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (shouldStopTocBlock(p)) {
                break;
            }
            String raw = p.getText();
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            int tocLevel = tocParagraphStyleLevel(p);
            if (tocLevel < 0) {
                break;
            }
            s.add(i);
        }
        return s;
    }

    /**
     * Нормализованные заголовки строк оглавления (без «СОДЕРЖАНИЕ»/«ОГЛАВЛЕНИЕ») — для ФТ-16.
     */
    public static List<String> tocLineTitleKeys(List<ParagraphInfo> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return List.of();
        }
        TocBlock block = resolveTocBlock(paragraphs);
        if (block == null || block.lines().isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (TocParsedLine line : block.lines()) {
            String key = normalizeCompareKey(line.titleText());
            if (!key.isEmpty() && !TOC_SECTION_TITLES.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * То же сопоставление заголовков, что при проверке соответствия оглавления тексту.
     */
    public static boolean titlesMatchTocToBody(String tocKey, String bodyKey) {
        return titlesMatch(tocKey, bodyKey);
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        issues.addAll(validateTocSectionTitlePresence(paragraphs));

        TocBlock block = resolveTocBlock(paragraphs);
        if (block == null || block.lines().isEmpty()) {
            issues.add("ФТ-7: не найден блок оглавления: нет подряд идущих строк вида «заголовок … N» "
                    + "(отточие и номер страницы) и нет абзацев со стилями TOC1–TOC3.");
            return issues;
        }

        int firstTitleIdx = findFirstTocSectionTitleIndex(paragraphs);
        if (firstTitleIdx >= 0 && block.startIndex() >= 0 && block.startIndex() != firstTitleIdx + 1) {
            issues.add("ФТ-7: заголовок раздела «СОДЕРЖАНИЕ»/«ОГЛАВЛЕНИЕ» должен стоять сразу перед строками оглавления.");
        }

        for (TocParsedLine line : block.lines()) {
            String key = normalizeCompareKey(line.titleText());
            if (key.isEmpty() || isTocSectionTitle(key)) {
                continue;
            }
            issues.addAll(matchTocLineToBody(line, paragraphs));
        }

        return issues;
    }

    /**
     * Ровно один абзац-заголовок «СОДЕРЖАНИЕ» или «ОГЛАВЛЕНИЕ»; не оба сразу по отдельности допускается одно слово один раз.
     */
    private static List<String> validateTocSectionTitlePresence(List<ParagraphInfo> paragraphs) {
        List<String> out = new ArrayList<>();
        int count = 0;
        for (ParagraphInfo p : paragraphs) {
            String t = normalizeTitle(p.getText());
            if (isTocSectionTitle(t)) {
                count++;
            }
        }
        if (count == 0) {
            out.add("ФТ-7: в документе нет заголовка раздела «СОДЕРЖАНИЕ» или «ОГЛАВЛЕНИЕ».");
        } else if (count > 1) {
            out.add("ФТ-7: заголовок «СОДЕРЖАНИЕ»/«ОГЛАВЛЕНИЕ» должен встречаться ровно один раз (найдено: " + count + ").");
        }
        return out;
    }

    private static int findFirstTocSectionTitleIndex(List<ParagraphInfo> paragraphs) {
        for (int i = 0; i < paragraphs.size(); i++) {
            if (isTocSectionTitle(normalizeTitle(paragraphs.get(i).getText()))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Блок оглавления: либо по стилям TOC1–3 (как раньше), либо самая длинная цепочка подряд абзацев,
     * которые парсятся как строка оглавления (текст + номер страницы).
     */
    private static TocBlock resolveTocBlock(List<ParagraphInfo> paragraphs) {
        List<TocParsedLine> byStyle = collectByTocStyles(paragraphs);
        List<TocParsedLine> byHeuristic = collectLongestContiguousHeuristic(paragraphs);

        if (byHeuristic.size() > byStyle.size() && !byHeuristic.isEmpty()) {
            return new TocBlock(findLongestHeuristicStartIndex(paragraphs), byHeuristic);
        }
        if (!byStyle.isEmpty()) {
            return new TocBlock(findTocStyleBlockStartIndex(paragraphs), byStyle);
        }
        if (!byHeuristic.isEmpty()) {
            return new TocBlock(findLongestHeuristicStartIndex(paragraphs), byHeuristic);
        }
        return null;
    }

    private static int findTocStyleBlockStartIndex(List<ParagraphInfo> paragraphs) {
        int first = findFirstTocStyledParagraphIndex(paragraphs);
        if (first < 0) {
            return -1;
        }
        if (first > 0 && isTocSectionTitle(normalizeTitle(paragraphs.get(first - 1).getText()))) {
            return first;
        }
        return first;
    }

    private static List<TocParsedLine> collectByTocStyles(List<ParagraphInfo> paragraphs) {
        int tocHeadingIdx = findTocHeadingIndex(paragraphs);
        int lineStart;
        if (tocHeadingIdx >= 0) {
            lineStart = tocHeadingIdx + 1;
        } else {
            int ft = findFirstTocStyledParagraphIndex(paragraphs);
            if (ft < 0) {
                return List.of();
            }
            lineStart = ft;
        }

        List<TocParsedLine> tocLines = new ArrayList<>();
        for (int i = lineStart; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (shouldStopTocBlock(p)) {
                break;
            }
            String raw = p.getText();
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            int tocLevel = tocParagraphStyleLevel(p);
            if (tocLevel < 0) {
                break;
            }
            TocParsedLine parsed = parseTocLine(raw, tocLevel, p);
            if (parsed != null) {
                tocLines.add(parsed);
            }
        }
        return tocLines;
    }

    /** Пустой абзац или оторванный номер страницы в разметке поля TOC — пропускаем, но не завершаем блок. */
    private static boolean isTocNoiseOrGapParagraph(ParagraphInfo p) {
        String raw = p.getText();
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String s = raw.replace('\u00A0', ' ').trim();
        return s.matches("^\\d{1,4}$");
    }

    /**
     * После заголовка «СОДЕРЖАНИЕ»: первая непустая строка, похожая на пункт оглавления.
     * Если первый же непустой абзац не подходит — оглавление по эвристике не задаётся (без глобального поиска).
     */
    private static int findFirstHeuristicTocLineAfterHeading(List<ParagraphInfo> paragraphs) {
        int h = findTocHeadingIndex(paragraphs);
        if (h < 0) {
            return -1;
        }
        for (int i = h + 1; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (isTocNoiseOrGapParagraph(p)) {
                continue;
            }
            if (canParseAsTocLineHeuristic(p)) {
                return i;
            }
            return -1;
        }
        return -1;
    }

    /** Границы полуинтервала [start, end): одна непрерывная цепочка эвристических строк оглавления. */
    private static int[] heuristicTocRunBounds(List<ParagraphInfo> paragraphs) {
        int n = paragraphs.size();
        int h = findTocHeadingIndex(paragraphs);
        if (h >= 0) {
            int start = findFirstHeuristicTocLineAfterHeading(paragraphs);
            if (start < 0) {
                return null;
            }
            int j = start;
            while (j < n) {
                ParagraphInfo p = paragraphs.get(j);
                if (canParseAsTocLineHeuristic(p)) {
                    j++;
                } else if (isTocNoiseOrGapParagraph(p)) {
                    j++;
                } else if (j > start && isAppendixTocLineWithoutPageNumber(p, paragraphs.get(j - 1))) {
                    j++;
                } else {
                    break;
                }
            }
            return new int[] {start, j};
        }
        int bestStart = -1;
        int bestLen = 0;
        for (int i = 0; i < n; i++) {
            if (!canParseAsTocLineHeuristic(paragraphs.get(i))) {
                continue;
            }
            int j = i;
            while (j < n && canParseAsTocLineHeuristic(paragraphs.get(j))) {
                j++;
            }
            int len = j - i;
            if (len > bestLen) {
                bestLen = len;
                bestStart = i;
            }
            i = j - 1;
        }
        if (bestStart < 0 || bestLen == 0) {
            return null;
        }
        int j = bestStart;
        while (j < n && canParseAsTocLineHeuristic(paragraphs.get(j))) {
            j++;
        }
        return new int[] {bestStart, j};
    }

    /**
     * Строка вида «ПРИЛОЖЕНИЕ X» без номера в том же абзаце (поле TOC), сразу после другой строки оглавления
     * на той же странице.
     */
    private static boolean isAppendixTocLineWithoutPageNumber(ParagraphInfo p, ParagraphInfo prev) {
        if (prev == null) {
            return false;
        }
        Integer pp = p.getPageIndex();
        Integer pv = prev.getPageIndex();
        if (pp != null && pv != null && !pp.equals(pv)) {
            return false;
        }
        String raw = p.getText();
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String norm = normalizeTitle(raw);
        if (!norm.matches("^ПРИЛОЖЕНИЕ\\s+[А-ЯA-Z].*")) {
            return false;
        }
        if (norm.length() > 80) {
            return false;
        }
        String trimmed = raw.replace('\u00A0', ' ').replace("\u200B", "").trim();
        if (LINE_WITH_LEADER.matcher(trimmed).matches()) {
            return false;
        }
        if (LINE_TAIL_PAGE.matcher(trimmed).matches()) {
            return false;
        }
        return true;
    }

    private static List<TocParsedLine> collectLongestContiguousHeuristic(List<ParagraphInfo> paragraphs) {
        int[] bounds = heuristicTocRunBounds(paragraphs);
        if (bounds == null) {
            return List.of();
        }
        int start = bounds[0];
        int end = bounds[1];
        List<TocParsedLine> lines = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (isTocNoiseOrGapParagraph(p)) {
                continue;
            }
            int level = inferTocLevel(p);
            TocParsedLine pl = parseTocLine(p.getText(), level, p);
            if (pl != null) {
                lines.add(pl);
            }
        }
        return lines;
    }

    private static int findLongestHeuristicStartIndex(List<ParagraphInfo> paragraphs) {
        int[] bounds = heuristicTocRunBounds(paragraphs);
        return bounds == null ? -1 : bounds[0];
    }

    /**
     * Эвристика без стилей TOC: отточие + номер страницы, либо «текст N» только для нумерованных пунктов
     * или коротких заголовков прописными (как в оглавлении).
     */
    private static boolean canParseAsTocLineHeuristic(ParagraphInfo p) {
        String raw = p.getText();
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        String norm = raw.replace('\u00A0', ' ').trim();
        if (LINE_WITH_LEADER.matcher(norm).matches()) {
            int level = inferTocLevel(p);
            return parseTocLine(raw, level, p) != null;
        }
        Matcher m2 = LINE_TAIL_PAGE.matcher(norm);
        if (!m2.matches()) {
            return false;
        }
        String title = m2.group("title").trim();
        if (title.matches("(?s)^\\d+([.]\\d+)*\\.?\\s+.*")) {
            int level = inferTocLevel(p);
            return parseTocLine(raw, level, p) != null;
        }
        if (title.length() <= 80 && title.equals(title.toUpperCase(Locale.ROOT)) && title.length() >= 3) {
            int level = inferTocLevel(p);
            return parseTocLine(raw, level, p) != null;
        }
        return false;
    }

    /** Уровень: стиль TOCn, иначе по виду номера (3.1 → 2, 1. → 1). */
    private static int inferTocLevel(ParagraphInfo p) {
        int st = tocParagraphStyleLevel(p);
        if (st >= 1) {
            return st;
        }
        String raw = p.getText();
        if (raw == null) {
            return 1;
        }
        String t = raw.replace('\u00A0', ' ').trim();
        if (t.matches("(?s)^\\d+\\.\\d+\\.\\d+.*")) {
            return 3;
        }
        if (t.matches("(?s)^\\d+\\.\\d+\\s.*")) {
            return 2;
        }
        return 1;
    }

    private static int findTocHeadingIndex(List<ParagraphInfo> paragraphs) {
        for (int i = 0; i < paragraphs.size(); i++) {
            if (isTocSectionTitle(normalizeTitle(paragraphs.get(i).getText()))) {
                return i;
            }
        }
        return -1;
    }

    private static int findFirstTocStyledParagraphIndex(List<ParagraphInfo> paragraphs) {
        for (int i = 0; i < paragraphs.size(); i++) {
            if (tocParagraphStyleLevel(paragraphs.get(i)) >= 1) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isTocSectionTitle(String normalizedUppercase) {
        return TOC_SECTION_TITLES.contains(normalizedUppercase);
    }

    private static boolean shouldStopTocBlock(ParagraphInfo p) {
        String t = normalizeTitle(p.getText());
        if (t.isEmpty()) {
            return false;
        }
        if (tocParagraphStyleLevel(p) >= 1) {
            return false;
        }
        if (!isStructuralSectionHeadingStyle(p)) {
            return false;
        }
        return !isTocSectionTitle(t);
    }

    private static boolean isStructuralSectionHeadingStyle(ParagraphInfo p) {
        if (p.getOutlineLevel() != null && p.getOutlineLevel() == 0) {
            return true;
        }
        String sn = p.getStyleName();
        if (sn == null) {
            return false;
        }
        String s = sn.toLowerCase(Locale.ROOT);
        return s.contains("заголовок") || s.contains("heading");
    }

    private static int tocParagraphStyleLevel(ParagraphInfo p) {
        String id = p.getStyleId();
        if (id == null) {
            return -1;
        }
        Matcher m = TOC_STYLE_ID.matcher(id.trim());
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private static List<String> matchTocLineToBody(TocParsedLine line, List<ParagraphInfo> paragraphs) {
        List<String> out = new ArrayList<>();
        String key = normalizeCompareKey(line.titleText());
        if (isTocSectionTitle(key)) {
            return out;
        }
        int expectedOutline = Math.max(0, line.tocStyleLevel() - 1);

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (tocParagraphStyleLevel(p) >= 1) {
                continue;
            }
            if (!couldBeBodyHeading(p)) {
                continue;
            }
            String bodyKey = normalizeCompareKey(p.getText());
            if (bodyKey.isEmpty()) {
                continue;
            }
            if (!titlesMatch(key, bodyKey)) {
                continue;
            }
            if (p.getOutlineLevel() != null) {
                if (!p.getOutlineLevel().equals(expectedOutline)) {
                    continue;
                }
            } else if (expectedOutline > 0) {
                continue;
            }
            candidates.add(i);
        }

        if (candidates.isEmpty()) {
            out.add(String.format(Locale.ROOT,
                    "ФТ-7: в тексте нет заголовка из оглавления «%s» (уровень TOC%d → outline %d) — "
                            + "в содержании есть пункт, которого нет в документе.",
                    shorten(line.titleText(), 80),
                    line.tocStyleLevel(),
                    expectedOutline));
            return out;
        }

        int first = candidates.get(0);
        ParagraphInfo match = paragraphs.get(first);
        if (!isRealHeadingParagraph(match)) {
            out.add(String.format(Locale.ROOT,
                    "ФТ-7: текст «%s» найден, но абзац не похож на заголовок (стиль='%s', outline=%s).",
                    shorten(line.titleText(), 80),
                    match.getStyleName(),
                    match.getOutlineLevel()));
        }

        Integer bodyPage = match.getPageIndex();
        if (bodyPage != null && line.pageNumber() != bodyPage) {
            out.add(String.format(Locale.ROOT,
                    "ФТ-7: в содержании для «%s» указана стр. %d, в документе заголовок начинается со стр. %d — "
                            + "в строке оглавления в шаблоне замените номер: вместо %d укажите %d.",
                    shorten(line.titleText(), 80),
                    line.pageNumber(),
                    bodyPage,
                    line.pageNumber(),
                    bodyPage));
        }
        return out;
    }

    private static boolean titlesMatch(String tocKey, String bodyKey) {
        if (tocKey.equals(bodyKey)) {
            return true;
        }
        if (bodyKey.startsWith(tocKey)) {
            return true;
        }
        if (tocKey.startsWith(bodyKey) && bodyKey.length() >= 5) {
            return true;
        }
        return tocKey.length() >= 10 && bodyKey.contains(tocKey);
    }

    private static boolean isRealHeadingParagraph(ParagraphInfo p) {
        if (p.getOutlineLevel() != null) {
            return true;
        }
        String sn = p.getStyleName();
        if (sn == null) {
            return false;
        }
        String s = sn.toLowerCase(Locale.ROOT);
        return s.contains("заголовок") || s.contains("heading");
    }

    private static boolean couldBeBodyHeading(ParagraphInfo p) {
        return p.getOutlineLevel() != null || isRealHeadingParagraph(p);
    }

    private static TocParsedLine parseTocLine(String raw, int tocLevel, ParagraphInfo sourcePara) {
        String normalized = raw.replace('\u00A0', ' ').replace("\u200B", "").trim();
        Matcher m = LINE_WITH_LEADER.matcher(normalized);
        if (m.matches()) {
            return new TocParsedLine(
                    m.group("title").trim(),
                    Integer.parseInt(m.group("page")),
                    true,
                    tocLevel,
                    sourcePara.getStyleId()
            );
        }
        Matcher m2 = LINE_TAIL_PAGE.matcher(normalized);
        if (m2.matches()) {
            return new TocParsedLine(
                    m2.group("title").trim(),
                    Integer.parseInt(m2.group("page")),
                    false,
                    tocLevel,
                    sourcePara.getStyleId()
            );
        }
        return null;
    }

    private static String normalizeTitle(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeCompareKey(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    private record TocBlock(int startIndex, List<TocParsedLine> lines) {}

    private record TocParsedLine(
            String titleText,
            int pageNumber,
            boolean hasLeaderDots,
            int tocStyleLevel,
            String tocStyleId
    ) {}
}
