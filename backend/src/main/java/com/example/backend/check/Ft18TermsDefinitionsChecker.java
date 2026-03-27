package com.example.backend.check;

import com.example.backend.config.checks.CheckSession;
import com.example.backend.domain.ParagraphInfo;

import com.example.backend.domain.TableInfo;



import java.util.ArrayList;

import java.util.List;

import java.util.Locale;

import java.util.TreeMap;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



/**

 * оформление списка терминов и определений (п. 4.9.2).

 * <p>

 * Допустим ровно один из двух вариантов (нельзя смешивать):

 * <ul>

 *   <li><b>Вариант 1</b> — элементы столбцом: отдельные абзацы вне таблицы; термин и определение разделены тире;

 *       термин с прописной буквы; по левому краю; в конце строки нет знаков препинания.</li>

 *   <li><b>Вариант 2</b> — таблица Word с двумя столбцами (термин | определение); первая строка заполнена;

 *       в остальных строках не должно быть пустых ячеек; в конце текста ячейки — без знаков препинания (как в варианте 1).</li>

 * </ul>

 * Раздел ищется по заголовку «СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ» (как в п. 3.2 / ФТ-16). Если раздела нет, замечаний нет.

 */

public final class Ft18TermsDefinitionsChecker {



    private static final String REQ = "п. 4.9.2 — список терминов и определений";



    private static final String V1 = "вариант 1 (список абзацами)";



    private static final String V2 = "вариант 2 (таблица)";



    private static final int MAX_ISSUES = 40;



    private static final Pattern DASH_SEPARATOR = Pattern.compile("[—–\\-]");



    /** Знаки препинания в конце строки (п. 4.9.2 — без точки и запятой; расширяем явно). */

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?…]+$");

    /** п. 4.9.2 — в конце строки/ячейки не должно быть знаков препинания. */
    private static boolean endsWithTrailingPunctuation(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        String end = raw.replaceAll("\\s+$", "");
        return !end.isEmpty() && TRAILING_PUNCTUATION.matcher(end).find();
    }

    private Ft18TermsDefinitionsChecker() {

    }



    /**
     * Одна строка для лога: найден ли раздел «СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ», индекс абзаца заголовка, граница, какой вариант.
     */
    public static String formatSectionDiagnostics(List<ParagraphInfo> paragraphs, List<TableInfo> tables) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return "нет абзацев — раздел «" + CheckSession.ft18().sectionTitle() + "» не искался.";
        }
        Integer h = findSectionHeadingIndex(paragraphs);
        if (h == null) {
            return "раздел «"
                    + CheckSession.ft18().sectionTitle()
                    + "» не найден: нет абзаца с таким текстом заголовка (учитываются точка/двоеточие в конце; "
                    + "дополнительно ищется совпадение по тексту, если нет outline level 0).";
        }
        int next = findNextOutline0HeadingIndex(paragraphs, h);
        ParagraphInfo head = paragraphs.get(h);
        String headPreview = shorten(safeTrim(head.getText()), 90);
        String ol = head.getOutlineLevel() == null ? "не задан" : String.valueOf(head.getOutlineLevel());
        boolean anyOut = false;
        boolean anyIn = false;
        for (int i = h + 1; i < next; i++) {
            ParagraphInfo p = paragraphs.get(i);
            String t = safeTrim(p.getText());
            if (t.isEmpty()) {
                continue;
            }
            if (p.isInTable()) {
                anyIn = true;
            } else {
                anyOut = true;
            }
        }
        String variantLine;
        if (anyOut && anyIn) {
            variantLine = "смешение: " + V1 + " и " + V2;
        } else if (anyOut) {
            variantLine = "обнаружен " + V1;
        } else if (anyIn) {
            variantLine = "обнаружен " + V2;
        } else {
            variantLine = "контент раздела пуст (только пустые строки)";
        }
        String rangeDesc;
        if (next <= h + 1) {
            rangeDesc = "между заголовком и следующим заголовком уровня 0 нет абзацев";
        } else {
            rangeDesc = String.format(Locale.ROOT, "контент раздела — абзацы с #%d по #%d", h + 1, next - 1);
        }
        return String.format(
                Locale.ROOT,
                "раздел найден — абзац заголовка #%d, outlineLevel=%s, текст: «%s»; %s; %s.",
                h,
                ol,
                headPreview,
                rangeDesc,
                variantLine);
    }

    /**
     * Если раздел найден и выбран один вариант (без смешения), возвращает короткую метку для логов;
     * иначе {@code null}.
     */
    public static String describeDetectedVariant(List<ParagraphInfo> paragraphs, List<TableInfo> tables) {

        if (paragraphs == null || paragraphs.isEmpty()) {

            return null;

        }

        Integer h = findSectionHeadingIndex(paragraphs);

        if (h == null) {

            return null;

        }

        int next = findNextOutline0HeadingIndex(paragraphs, h);

        boolean anyOut = false;

        boolean anyIn = false;

        for (int i = h + 1; i < next; i++) {

            ParagraphInfo p = paragraphs.get(i);

            String t = safeTrim(p.getText());

            if (t.isEmpty()) {

                continue;

            }

            if (p.isInTable()) {

                anyIn = true;

            } else {

                anyOut = true;

            }

        }

        if (anyOut && anyIn) {

            return null;

        }

        if (anyOut) {

            return V1;

        }

        if (anyIn) {

            return V2;

        }

        return null;

    }



    public static List<String> check(List<ParagraphInfo> paragraphs, List<TableInfo> tables) {

        List<String> issues = new ArrayList<>();

        if (paragraphs == null || paragraphs.isEmpty()) {

            return issues;

        }

        Integer h = findSectionHeadingIndex(paragraphs);

        if (h == null) {

            return issues;

        }

        int next = findNextOutline0HeadingIndex(paragraphs, h);

        if (next == h + 1) {

            issues.add(

                    ""

                            + REQ

                            + " — раздел «"

                            + CheckSession.ft18().sectionTitle()

                            + "» пуст: сразу начинается следующий раздел. "

                            + "Как исправить: добавьте список ("

                            + V1

                            + ": абзацы «Термин — расшифровка») или таблицу ("

                            + V2

                            + ").");

            return issues;

        }



        boolean anyOut = false;

        boolean anyIn = false;

        for (int i = h + 1; i < next; i++) {

            ParagraphInfo p = paragraphs.get(i);

            String t = safeTrim(p.getText());

            if (t.isEmpty()) {

                continue;

            }

            if (p.isInTable()) {

                anyIn = true;

            } else {

                anyOut = true;

            }

        }



        if (!anyOut && !anyIn) {

            issues.add(

                    ""

                            + REQ

                            + " — в разделе «"

                            + CheckSession.ft18().sectionTitle()

                            + "» нет ни одной непустой строки. "

                            + "Как исправить: заполните список абзацами или таблицей.");

            return issues;

        }



        if (anyOut && anyIn) {

            issues.add(

                    ""

                            + REQ

                            + " — обнаружено смешение: одновременно "

                            + V1

                            + " и "

                            + V2

                            + ". "

                            + "Как исправить: оставьте только абзацы столбцом (без таблицы) или только таблицу с двумя столбцами; удалите лишнее.");

            return issues;

        }



        if (anyOut) {

            issues.addAll(checkVariant1ParagraphList(paragraphs, h, next));

        } else {

            issues.addAll(checkVariant2Tables(paragraphs, tables == null ? List.of() : tables, h, next));

        }



        return issues;

    }



    private static List<String> checkVariant1ParagraphList(List<ParagraphInfo> paragraphs, int h, int next) {

        List<String> issues = new ArrayList<>();

        for (int i = h + 1; i < next; i++) {

            ParagraphInfo p = paragraphs.get(i);

            if (p.isInTable()) {

                continue;

            }

            String raw = safeTrim(p.getText());

            if (raw.isEmpty()) {

                continue;

            }

            String al = p.getAlignment();

            if (al != null && !"LEFT".equalsIgnoreCase(al)) {

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — строка «%s» (стр. %s): выравнивание не по левому краю (%s). "

                                        + "Как исправить: выделите абзацы списка → «Главная» → по левому краю.",

                                V1,

                                REQ,

                                shorten(raw, 70),

                                formatPage(p.getPageIndex()),

                                al));

            }

            Matcher dashM = DASH_SEPARATOR.matcher(raw);

            if (!dashM.find()) {

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — строка «%s»: нет тире между термином и расшифровкой. "

                                        + "Как исправить: оформите как «БД — база данных» (длинное/короткое тире или дефис).",

                                V1,

                                REQ,

                                shorten(raw, 80)));

                if (issues.size() >= MAX_ISSUES) {

                    break;

                }

                continue;

            }

            int dashPos = dashM.start();

            String termPart = raw.substring(0, dashPos).trim();

            String defPart = raw.substring(dashM.end()).trim();

            if (defPart.isEmpty()) {

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — строка «%s»: после тире нет расшифровки. "

                                        + "Как исправить: добавьте текст определения после тире.",

                                V1,

                                REQ,

                                shorten(raw, 80)));

            }

            int firstLetter = firstLetterCodepointIndex(termPart);

            if (firstLetter < 0) {

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — в термине «%s» нет буквы (проверка прописной буквы). "

                                        + "Как исправить: термин должен начинаться с буквы в верхнем регистре.",

                                V1,

                                REQ,

                                shorten(termPart, 60)));

            } else {

                int cp = termPart.codePointAt(firstLetter);

                if (Character.isLetter(cp) && !Character.isUpperCase(cp)) {

                    issues.add(

                            String.format(

                                    Locale.ROOT,

                                    "обнаружен %s. %s — термин «%s» должен начинаться с прописной буквы. "

                                            + "Как исправить: исправьте первую букву термина (до тире) на заглавную.",

                                    V1,

                                    REQ,

                                    shorten(termPart, 60)));

                }

            }

            if (endsWithTrailingPunctuation(raw)) {

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — в конце строки «%s» не должно быть знаков препинания (п. 4.9.2). "

                                        + "Как исправить: удалите точку, запятую и другие знаки в конце строки.",

                                V1,

                                REQ,

                                shorten(raw, 80)));

            }

            if (issues.size() >= MAX_ISSUES) {

                break;

            }

        }

        return issues;

    }



    /** Индекс первого кодпоинта, который является буквой; иначе -1. */

    private static int firstLetterCodepointIndex(String s) {

        if (s == null || s.isEmpty()) {

            return -1;

        }

        for (int i = 0; i < s.length(); ) {

            int cp = s.codePointAt(i);

            if (Character.isLetter(cp)) {

                return i;

            }

            i += Character.charCount(cp);

        }

        return -1;

    }



    private static List<String> checkVariant2Tables(

            List<ParagraphInfo> paragraphs, List<TableInfo> tables, int h, int next) {

        List<String> issues = new ArrayList<>();

        List<TableInfo> inRange = new ArrayList<>();

        for (TableInfo t : tables) {

            if (tableIntersectsAbbreviationsSection(t, h, next)) {

                inRange.add(t);

            }

        }

        if (inRange.isEmpty()) {

            issues.add(

                    "обнаружен "

                            + V2

                            + ". "

                            + REQ

                            + " — не найдена таблица Word, пересекающаяся с разделом «"

                            + CheckSession.ft18().sectionTitle()

                            + "». "

                            + "Как исправить: вставьте таблицу с двумя столбцами сразу под заголовком раздела.");

            return issues;

        }

        for (int ti = 0; ti < inRange.size(); ti++) {

            TableInfo t = inRange.get(ti);

            Integer cc = t.getColumnCount();

            if (cc == null || cc < 2) {

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — таблица №%d (начало на стр. %s): нужны два столбца (термин и расшифровка). "

                                        + "Как исправить: «Макет таблицы» → «Добавить столбец справа» или таблица 2×N.",

                                V2,

                                REQ,

                                ti + 1,

                                tableStartPage(t, paragraphs)));

                if (issues.size() >= MAX_ISSUES) {

                    break;

                }

                continue;

            }

            issues.addAll(checkVariant2TableCells(paragraphs, t, ti + 1, cc));

            if (issues.size() >= MAX_ISSUES) {

                break;

            }

        }

        return issues;

    }



    /**

     * Первая строка: в столбце 1 — термин, в столбце 2 — определение (оба не пусты).

     * Во всех строках: каждая ячейка каждого столбца не пуста; пустые ячейки перечисляются по номерам столбцов (1…N).

     */

    private static List<String> checkVariant2TableCells(

            List<ParagraphInfo> paragraphs, TableInfo t, int tableOrdinal1Based, int columnCount) {

        List<String> issues = new ArrayList<>();

        int start = t.getParagraphIndex();

        int end = t.getParagraphIndexEndExclusive();

        if (start < 0 || end > paragraphs.size() || start >= end) {

            issues.add(

                    String.format(

                            Locale.ROOT,

                            "обнаружен %s. %s — таблица №%d: не удалось сопоставить ячейки разбору.",

                            V2,

                            REQ,

                            tableOrdinal1Based));

            return issues;

        }



        TreeMap<Integer, TreeMap<Integer, StringBuilder>> rows = new TreeMap<>();

        for (int pi = start; pi < end; pi++) {

            ParagraphInfo p = paragraphs.get(pi);

            if (!p.isInTable()) {

                continue;

            }

            Integer r = p.getTableRowIndex();

            Integer c = p.getTableColumnIndex();

            if (r == null || c == null) {

                continue;

            }

            String txt = p.getText() == null ? "" : p.getText().trim();

            TreeMap<Integer, StringBuilder> rowMap = rows.computeIfAbsent(r, k -> new TreeMap<>());

            StringBuilder cellBuf = rowMap.computeIfAbsent(c, k -> new StringBuilder());

            if (!txt.isEmpty()) {

                if (cellBuf.length() > 0) {

                    cellBuf.append(' ');

                }

                cellBuf.append(txt);

            }

        }



        if (rows.isEmpty()) {

            issues.add(

                    String.format(

                            Locale.ROOT,

                            "обнаружен %s. %s — таблица №%d: нет данных по строкам/столбцам (проверьте сохранение как .docx).",

                            V2,

                            REQ,

                            tableOrdinal1Based));

            return issues;

        }



        int lastRowFromData = rows.isEmpty() ? -1 : rows.lastKey();

        Integer tr = t.getRowCount();

        int maxRowIndex = Math.max(lastRowFromData, tr == null || tr <= 0 ? lastRowFromData : tr - 1);

        for (int r = 0; r <= maxRowIndex; r++) {

            TreeMap<Integer, StringBuilder> cells = rows.getOrDefault(r, new TreeMap<>());

            List<Integer> emptyCols = new ArrayList<>();

            for (int c = 0; c < columnCount; c++) {

                StringBuilder sb = cells.get(c);

                String cellText = sb == null ? "" : sb.toString().trim();

                if (cellText.isEmpty()) {

                    emptyCols.add(c + 1);

                }

            }

            if (emptyCols.isEmpty()) {

                continue;

            }

            String emptyDesc = formatColumnList(emptyCols);

            int rowUser = r + 1;

            if (r == 0) {

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — таблица №%d, строка %d (первая): должны быть заполнены термин (столбец 1) и определение (столбец 2); пустые столбцы: %s. "

                                        + "Как исправить: введите текст в указанные ячейки.",

                                V2,

                                REQ,

                                tableOrdinal1Based,

                                rowUser,

                                emptyDesc));

            } else {

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — таблица №%d, строка %d: пустые столбцы: %s. "

                                        + "Как исправить: заполните все ячейцы строки или удалите лишнюю строку.",

                                V2,

                                REQ,

                                tableOrdinal1Based,

                                rowUser,

                                emptyDesc));

            }

            if (issues.size() >= MAX_ISSUES) {

                return issues;

            }

        }

        for (int r = 0; r <= maxRowIndex; r++) {

            TreeMap<Integer, StringBuilder> cells = rows.getOrDefault(r, new TreeMap<>());

            for (int c = 0; c < columnCount; c++) {

                StringBuilder sb = cells.get(c);

                String cellText = sb == null ? "" : sb.toString().trim();

                if (cellText.isEmpty() || !endsWithTrailingPunctuation(cellText)) {

                    continue;

                }

                issues.add(

                        String.format(

                                Locale.ROOT,

                                "обнаружен %s. %s — таблица №%d, строка %d, столбец %d: в конце текста ячейки не должно быть знаков препинания (п. 4.9.2). "

                                        + "Фрагмент: «%s». Как исправить: удалите знак в конце ячейки.",

                                V2,

                                REQ,

                                tableOrdinal1Based,

                                r + 1,

                                c + 1,

                                shorten(cellText, 70)));

                if (issues.size() >= MAX_ISSUES) {

                    return issues;

                }

            }

        }

        return issues;

    }

    private static String formatColumnList(List<Integer> oneBasedColumns) {

        if (oneBasedColumns.isEmpty()) {

            return "—";

        }

        return oneBasedColumns.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));

    }



    /**

     * Таблица пересекает интервал абзацев раздела (h+1 … next-1), в т.ч. если таблица началась до заголовка,

     * но её ячейки попадают в раздел.

     */

    private static boolean tableIntersectsAbbreviationsSection(TableInfo t, int h, int next) {

        int t0 = t.getParagraphIndex();

        int t1 = t.getParagraphIndexEndExclusive();

        return t0 < next && t1 > h + 1;

    }



    private static String tableStartPage(TableInfo t, List<ParagraphInfo> paragraphs) {

        int idx = t.getParagraphIndex();

        if (idx >= 0 && idx < paragraphs.size()) {

            Integer p = paragraphs.get(idx).getPageIndex();

            if (p != null) {

                return p.toString();

            }

        }

        return t.getPageIndex() > 0 ? Integer.toString(t.getPageIndex()) : "не определена";

    }



    private static Integer findSectionHeadingIndex(List<ParagraphInfo> paragraphs) {

        for (int i = 0; i < paragraphs.size(); i++) {

            ParagraphInfo p = paragraphs.get(i);

            if (!isBodyOutlineLevel0(p)) {

                continue;

            }

            if (CheckSession.ft18().sectionTitle().equals(normalizeTitle(p.getText()))) {

                return i;

            }

        }

        for (int i = 0; i < paragraphs.size(); i++) {

            ParagraphInfo p = paragraphs.get(i);

            if (CheckSession.ft18().sectionTitle().equals(normalizeTitle(p.getText()))) {

                return i;

            }

        }

        return null;

    }



    private static int findNextOutline0HeadingIndex(List<ParagraphInfo> paragraphs, int h) {

        for (int i = h + 1; i < paragraphs.size(); i++) {

            ParagraphInfo p = paragraphs.get(i);

            if (isBodyOutlineLevel0(p) && !safeTrim(p.getText()).isEmpty()) {

                return i;

            }

        }

        return paragraphs.size();

    }



    private static boolean isBodyOutlineLevel0(ParagraphInfo p) {

        Integer ol = p.getOutlineLevel();

        return ol != null && ol == 0;

    }



    private static String normalizeTitle(String text) {

        if (text == null) {

            return "";

        }

        String t = text.replace('\u00A0', ' ')

                .replaceAll("\\s+", " ")

                .trim()

                .toUpperCase(Locale.ROOT);

        t = t.replaceAll("[.:;]+\\s*$", "").trim();

        return t;

    }



    private static String safeTrim(String text) {

        return text == null ? "" : text.replace('\u00A0', ' ').trim();

    }



    private static String formatPage(Integer page) {

        return page == null ? "не определена" : page.toString();

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

}

