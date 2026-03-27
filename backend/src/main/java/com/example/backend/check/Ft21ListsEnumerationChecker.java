package com.example.backend.check;

import com.example.backend.config.checks.CheckSession;
import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * оформление маркированных и нумерованных списков (п. Приложение 1 / п. 3.2 в ТЗ).
 * <p>
 * Обрабатываются все абзацы с нумерацией Word ({@code w:numPr}). Один список — это <b>непрерывная цепочка</b> абзацев
 * со списком (без обычного текста между пунктами). Word иногда задаёт разным пунктам разный {@code numId} — такие фрагменты
 * всё равно считаются одним списком, если идут подряд.
 * Единообразие маркеров проверяется и <b>по всему списку</b> (все уровни вместе): смешение нумерации и маркеров (▪) в одном
 * списке фиксируется, даже если на каждом уровне отдельно всё однородно.
 */
public final class Ft21ListsEnumerationChecker {

    private static final String REQ = "п. Приложение 1 — перечисления и списки";

    /** Лимит сообщений за прогон (технический порог, не из ТЗ). */
    private static final int MAX_ISSUES = 80;

    private Ft21ListsEnumerationChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        return check(paragraphs, CheckSession.ft21());
    }

    public static List<String> check(List<ParagraphInfo> paragraphs, Ft21ListParams params) {
        List<String> issues = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return issues;
        }
        Ft21ListParams p = params != null ? params : Ft21ListParams.defaults();

        List<List<Integer>> runs = splitListRuns(paragraphs);
        int runOrdinal = 0;
        for (List<Integer> run : runs) {
            runOrdinal++;
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            String listCtx = describeListContext(paragraphs, run, runOrdinal);
            Map<String, Integer> entire = buildFmtEntireRun(paragraphs, run);
            if (entire.size() > 1) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "%s — %s по всему списку смешаны разные типы маркеров/нумерации: %s.",
                                REQ,
                                listCtx,
                                formatFmtCounts(entire)));
            }

            Map<Integer, Map<String, Integer>> fmtByLevel = buildFmtByLevel(paragraphs, run);
            for (Map.Entry<Integer, Map<String, Integer>> e : fmtByLevel.entrySet()) {
                int level = e.getKey();
                Map<String, Integer> counts = e.getValue();
                if (counts.size() > 1) {
                    issues.add(
                            String.format(
                                    Locale.ROOT,
                                    "%s — %s на одном уровне (ilvl=%d) разные маркеры: %s.",
                                    REQ,
                                    listCtx,
                                    level,
                                    formatFmtCounts(counts)));
                }
            }

            List<Integer> level0 = new ArrayList<>();
            for (int idx : run) {
                ParagraphInfo par = paragraphs.get(idx);
                int ilvl = par.getNumberingIlvl() != null ? par.getNumberingIlvl() : 0;
                if (ilvl == 0) {
                    level0.add(idx);
                }
            }
            checkIndentsForLevel0(issues, paragraphs, level0, listCtx, p);
            checkPunctuationLevel0(issues, paragraphs, level0, listCtx, p);
        }

        return issues;
    }

    private static String describeListContext(List<ParagraphInfo> paragraphs, List<Integer> run, int runOrdinal) {
        ParagraphInfo first = paragraphs.get(run.get(0));
        int startDocParagraph = run.get(0) + 1;
        String preview = shorten(safeText(first.getText()), 55);
        String numIdStr = formatNumIdsSummary(paragraphs, run);
        return String.format(
                Locale.ROOT,
                "список №%d (numId=%s, начинается с абзаца №%d, начало «%s», стр. %s)",
                runOrdinal,
                numIdStr,
                startDocParagraph,
                preview,
                formatPage(first.getPageIndex()));
    }

    private static String formatNumIdsSummary(List<ParagraphInfo> paragraphs, List<Integer> run) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (int idx : run) {
            Integer id = paragraphs.get(idx).getNumberingNumId();
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return "?";
        }
        if (ids.size() == 1) {
            return String.valueOf(ids.iterator().next());
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "))
                + " (несколько numId в одном подряд идущем блоке)";
    }

    private static String safeText(String t) {
        return t == null ? "" : t.replace('\u00A0', ' ').trim();
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String x = s.replace('\n', ' ').trim();
        if (x.length() <= max) {
            return x.isEmpty() ? "…" : x;
        }
        return x.substring(0, max) + "…";
    }

    /** Сводка форматов по всем абзацам списка (все уровни). */
    private static Map<String, Integer> buildFmtEntireRun(List<ParagraphInfo> paragraphs, List<Integer> run) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int idx : run) {
            String fmt = paragraphs.get(idx).getListNumberingFmt();
            if (fmt == null || fmt.isBlank()) {
                fmt = "неизвестно";
            }
            m.merge(fmt, 1, Integer::sum);
        }
        return m;
    }

    private static String formatFmtCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(e -> humanizeNumFmt(e.getKey()) + " — " + e.getValue() + " абз.")
                .collect(Collectors.joining("; "));
    }

    private static void checkIndentsForLevel0(
            List<String> issues,
            List<ParagraphInfo> paragraphs,
            List<Integer> level0,
            String listCtx,
            Ft21ListParams params) {
        for (int idx : level0) {
            if (issues.size() >= MAX_ISSUES) {
                return;
            }
            ParagraphInfo p = paragraphs.get(idx);
            Double fi = p.getFirstLineIndentCm();
            Double li = p.getLeftIndentCm();
            if (fi == null && li == null) {
                continue;
            }
            boolean ok =
                    (fi != null && fi >= params.indentMinCm())
                            || (li != null && li >= params.indentMinCm())
                            || (fi != null
                                    && Math.abs(fi - params.indentCmExpected()) <= params.indentCmEpsilon()
                                    && li != null
                                    && li >= 0.3);
            if (!ok) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "%s — %s; абзац документа №%d (стр. %s): для пункта уровня 0 ожидается "
                                        + "заметный абзацный/левый отступ (ориентир ~%.2f см как в основном тексте); "
                                        + "фактически красная строка %s см, левый отступ %s см.",
                                REQ,
                                listCtx,
                                idx + 1,
                                formatPage(p.getPageIndex()),
                                params.indentCmExpected(),
                                fi == null ? "нет" : String.format(Locale.ROOT, "%.2f", fi),
                                li == null ? "нет" : String.format(Locale.ROOT, "%.2f", li)));
            }
        }
    }

    /**
     * «Мелкий» пункт: размер шрифта заметно меньше максимального в этом списке; если размеры совпадают — по длине текста
     * ниже порога относительно медианы и максимума.
     */
    private static void checkPunctuationLevel0(
            List<String> issues,
            List<ParagraphInfo> paragraphs,
            List<Integer> level0,
            String listCtx,
            Ft21ListParams params) {
        if (level0.size() <= 1) {
            return;
        }
        int n = level0.size();
        List<Double> sizes = new ArrayList<>(n);
        List<Integer> lengths = new ArrayList<>(n);
        for (int idx : level0) {
            String t = paragraphs.get(idx).getText();
            lengths.add(t == null ? 0 : t.replace('\u00A0', ' ').trim().length());
            sizes.add(paragraphs.get(idx).getFontSizePt());
        }
        List<Double> nonNullSizes = new ArrayList<>();
        for (Double s : sizes) {
            if (s != null) {
                nonNullSizes.add(s);
            }
        }
        double maxS = 0;
        double minS = Double.MAX_VALUE;
        for (Double s : nonNullSizes) {
            maxS = Math.max(maxS, s);
            minS = Math.min(minS, s);
        }
        boolean fontSpread = nonNullSizes.size() >= 2 && maxS - minS > 0.18;

        List<Integer> sortedLens = new ArrayList<>(lengths);
        sortedLens.sort(Integer::compareTo);
        int medianLen = sortedLens.get(sortedLens.size() / 2);
        int maxLen = 0;
        for (int L : lengths) {
            maxLen = Math.max(maxLen, L);
        }

        for (int i = 0; i < n; i++) {
            if (issues.size() >= MAX_ISSUES) {
                return;
            }
            int idx = level0.get(i);
            ParagraphInfo p = paragraphs.get(idx);
            String raw = p.getText();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.replace('\u00A0', ' ').trim();
            boolean isLast = i == n - 1;
            boolean small = isSmallItem(sizes.get(i), lengths.get(i), fontSpread, maxS, medianLen, maxLen, isLast);

            char last = lastSignificantChar(trimmed);
            if (isLast) {
                if (last == ';' || last == '；') {
                    issues.add(
                            String.format(
                                    Locale.ROOT,
                                    "%s — %s; последний пункт уровня 0 (абзац №%d, стр. %s): "
                                            + "завершение списка — нужна точка в конце, а не «;».",
                                    REQ,
                                    listCtx,
                                    idx + 1,
                                    formatPage(p.getPageIndex())));
                } else if (!isAcceptableClosingSentencePunct(last)) {
                    String lastWord = lastWordWithoutClosingPunctuation(trimmed);
                    issues.add(
                            String.format(
                                    Locale.ROOT,
                                    "%s — %s; последний пункт уровня 0 (абзац №%d, стр. %s): "
                                            + "ожидается точка в конце элемента (или ! ?); последнее слово без завершающей точки: «%s».",
                                    REQ,
                                    listCtx,
                                    idx + 1,
                                    formatPage(p.getPageIndex()),
                                    lastWord));
                }
            } else {
                if (small) {
                    if (last != ';' && last != '；') {
                        issues.add(
                                String.format(
                                        Locale.ROOT,
                                        "%s — %s; «мелкий» пункт уровня 0 (абзац №%d, стр. %s): ожидается «;» в конце.",
                                        REQ,
                                        listCtx,
                                        idx + 1,
                                        formatPage(p.getPageIndex())));
                    }
                }
            }
        }
    }

    /** Конец завершающего пункта: точка, вопрос, восклицание, кавычка, многоточие и т.п. */
    private static boolean isAcceptableClosingSentencePunct(char c) {
        return c == '.'
                || c == '!'
                || c == '?'
                || c == '»'
                || c == '”'
                || c == '"'
                || c == ')'
                || c == '…'
                || c == '›';
    }

    private static boolean isSmallItem(
            Double fontPt,
            int len,
            boolean fontSpread,
            double maxFont,
            int medianLen,
            int maxLen,
            boolean isLast) {
        if (isLast) {
            return false;
        }
        if (fontSpread && fontPt != null && fontPt < maxFont - 0.06) {
            return true;
        }
        int thr = Math.max((int) (medianLen * 0.72), (int) (maxLen * 0.42));
        if (thr < 12) {
            thr = 12;
        }
        return len < thr && len + 5 < maxLen;
    }

    private static char lastSignificantChar(String trimmed) {
        int end = trimmed.length();
        while (end > 0) {
            char c = trimmed.charAt(end - 1);
            if (!Character.isWhitespace(c)) {
                return c;
            }
            end--;
        }
        return 0;
    }

    /**
     * Для сообщения об отсутствии точки: показываем последнее слово целиком, а не один символ (иначе для «Рофлс» было бы «с»).
     * С конца слова снимаются типичные знаки, не входящие в само слово (запятая и т.п.).
     */
    private static String lastWordWithoutClosingPunctuation(String trimmed) {
        if (trimmed == null || trimmed.isBlank()) {
            return "пусто";
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return "пусто";
        }
        String w = stripTrailingNonWordPunctuation(parts[parts.length - 1]);
        return w.isEmpty() ? "…" : w;
    }

    private static String stripTrailingNonWordPunctuation(String w) {
        if (w == null || w.isEmpty()) {
            return w;
        }
        int end = w.length();
        while (end > 0) {
            char c = w.charAt(end - 1);
            if (c == '.'
                    || c == ','
                    || c == ';'
                    || c == ':'
                    || c == '!'
                    || c == '?'
                    || c == '»'
                    || c == '"'
                    || c == '”'
                    || c == ')'
                    || c == '…'
                    || c == '›') {
                end--;
            } else {
                break;
            }
        }
        return w.substring(0, end);
    }

    private static String formatPage(Integer page) {
        return page == null ? "не определена" : page.toString();
    }

    private static String humanizeNumFmt(String fmt) {
        if (fmt == null || fmt.isBlank()) {
            return "неизвестно";
        }
        return switch (fmt.toLowerCase(Locale.ROOT)) {
            case "decimal" -> "арабские цифры (decimal)";
            case "bullet" -> "маркер (bullet)";
            case "lowerletter" -> "строчные буквы";
            case "upperletter" -> "прописные буквы";
            case "lowerroman" -> "римские строчные";
            case "upperroman" -> "римские прописные";
            default -> fmt;
        };
    }

    /**
     * Непрерывные участки: все подряд идущие абзацы со списком (любой {@code numId}). Разделитель — только не-список.
     */
    private static List<List<Integer>> splitListRuns(List<ParagraphInfo> paragraphs) {
        List<List<Integer>> runs = new ArrayList<>();
        int i = 0;
        int n = paragraphs.size();
        while (i < n) {
            if (!paragraphs.get(i).isNumberingListParagraph()) {
                i++;
                continue;
            }
            List<Integer> run = new ArrayList<>();
            while (i < n && paragraphs.get(i).isNumberingListParagraph()) {
                run.add(i);
                i++;
            }
            runs.add(run);
        }
        return runs;
    }

    private static Map<Integer, Map<String, Integer>> buildFmtByLevel(List<ParagraphInfo> paragraphs, List<Integer> run) {
        Map<Integer, Map<String, Integer>> fmtByLevel = new TreeMap<>();
        for (int idx : run) {
            ParagraphInfo p = paragraphs.get(idx);
            Integer ilvl = p.getNumberingIlvl() != null ? p.getNumberingIlvl() : 0;
            String fmt = p.getListNumberingFmt();
            if (fmt == null || fmt.isBlank()) {
                fmt = "неизвестно";
            }
            fmtByLevel.computeIfAbsent(ilvl, k -> new LinkedHashMap<>()).merge(fmt, 1, Integer::sum);
        }
        return fmtByLevel;
    }
}
