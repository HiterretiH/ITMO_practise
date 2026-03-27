package com.example.backend.check;

import com.example.backend.config.checks.CheckSession;
import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ФТ-20: проверка списка использованных источников (п. 4.10).
 * <p>
 * Заголовок раздела: «СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ». Ссылки в тексте — только вида {@code [n]} (квадратные скобки).
 * Записи списка учитываются в двух вариантах:
 * <ul>
 *   <li>абзацы нумерованного списка Word ({@code w:numPr}), номер не обязан быть в {@link ParagraphInfo#getText()};</li>
 *   <li>явный текст в начале строки: {@code n. } (арабская цифра, точка, пробел).</li>
 * </ul>
 * Маркированные списки (bullet) не считаются записями библиографии. Вложенные уровни ({@code ilvl} &gt; 0) пропускаются.
 * <p>
 * Ссылки {@code [n]} ищутся по <b>всему</b> тексту документа ({@code fullText} или объединение абзацев), без обрезки «до списка» —
 * достаточно любого вхождения подстроки {@code [n]}.
 */
public final class Ft20BibliographyChecker {

    private static final String REQ = "п. 4.10 — список использованных источников";

    private static final int MAX_ISSUES = 50;

    private static final Pattern BRACKET_CITE = Pattern.compile("\\[(\\d+)\\]");

    /** Явная нумерация в тексте абзаца: «1. Автор…». */
    private static final Pattern BIB_ENTRY_TEXT_START = Pattern.compile("^(\\d+)\\.\\s+");

    private Ft20BibliographyChecker() {
    }

    /**
     * Одна строка для лога: найден ли раздел, сколько записей, сколько из списка Word и сколько с явным «n.» в тексте.
     */
    public static String formatSectionDiagnostics(List<ParagraphInfo> paragraphs, String fullText) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return "ФТ-20: нет абзацев — раздел «" + CheckSession.ft20().sectionTitle() + "» не искался.";
        }
        Integer h = findSectionHeadingIndex(paragraphs);
        if (h == null) {
            return "ФТ-20: раздел «" + CheckSession.ft20().sectionTitle() + "» не найден (заголовок уровня 0 или совпадение по тексту).";
        }
        int next = findNextOutline0HeadingIndex(paragraphs, h);
        BibEntriesResult parsed = parseBibliographyEntries(paragraphs, h + 1, next);
        return String.format(
                Locale.ROOT,
                "ФТ-20: раздел найден — абзац заголовка №%d; записей источников: %d "
                        + "(нумерованный список Word: %d; явный текст «n.» в начале строки: %d).",
                h + 1,
                parsed.numbers().size(),
                parsed.fromWordList(),
                parsed.fromTextPattern());
    }

    /**
     * Строки для лога: какие номера {@code [n]} найдены в документе и по каждому источнику из списка — есть ли {@code [n]} в тексте.
     */
    public static List<String> formatCitationMatrixLines(List<ParagraphInfo> paragraphs, String fullText) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return List.of();
        }
        Integer h = findSectionHeadingIndex(paragraphs);
        if (h == null) {
            return List.of();
        }
        int next = findNextOutline0HeadingIndex(paragraphs, h);
        return buildCitationMatrixLines(paragraphs, fullText, h, next);
    }

    public static List<String> check(List<ParagraphInfo> paragraphs, String fullText) {
        List<String> issues = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return issues;
        }

        Integer h = findSectionHeadingIndex(paragraphs);
        if (h == null) {
            issues.add(
                    "ФТ-20: "
                            + REQ
                            + " — не найден заголовок раздела «"
                            + CheckSession.ft20().sectionTitle()
                            + "» (ожидается заголовок уровня 0 или совпадение текста без учёта регистра).");
            return issues;
        }

        int next = findNextOutline0HeadingIndex(paragraphs, h);
        String entireDoc = entireDocumentText(fullText, paragraphs);
        Set<Integer> cited = extractBracketNumbers(entireDoc);
        BibEntriesResult parsed = parseBibliographyEntries(paragraphs, h + 1, next);
        List<Integer> bibNums = parsed.numbers();

        if (bibNums.isEmpty()) {
            issues.add(
                    String.format(
                            Locale.ROOT,
                            "ФТ-20: %s — раздел найден (абзац заголовка №%d), но не обнаружено ни одной записи источника: "
                                    + "нужны абзацы нумерованного списка Word (не маркированного) или строки, начинающиеся с «1. », «2. », … "
                                    + "(арабская цифра, точка, пробел в начале строки).",
                            REQ,
                            h + 1));
        } else {
            for (int i = 0; i < bibNums.size(); i++) {
                int expected = i + 1;
                int actual = bibNums.get(i);
                if (actual != expected) {
                    issues.add(
                            String.format(
                                    Locale.ROOT,
                                    "ФТ-20: %s — нумерация источников по порядку: ожидался номер %d после предыдущей записи, "
                                            + "фактически в списке идёт %d (п. 4.10: сквозная нумерация арабскими цифрами).",
                                    REQ,
                                    expected,
                                    actual));
                    break;
                }
            }
            Set<Integer> unique = new LinkedHashSet<>(bibNums);
            if (unique.size() != bibNums.size()) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "ФТ-20: %s — в списке источников повторяются номера записей.",
                                REQ));
            }
        }

        Set<Integer> bibSet = new LinkedHashSet<>(bibNums);
        for (int n : cited) {
            if (!bibSet.contains(n)) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "ФТ-20: %s — в тексте документа есть ссылка [%d], "
                                        + "но записи с номером %d в списке источников не найдено (нумерованный список Word и строки «%d. …»).",
                                REQ,
                                n,
                                n,
                                n));
                if (issues.size() >= MAX_ISSUES) {
                    return issues;
                }
            }
        }

        for (int n : bibNums) {
            if (!cited.contains(n)) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "ФТ-20: %s — в списке есть источник №%d, но во всём тексте документа нет вхождения [%d] "
                                        + "(ищется подстрока в квадратных скобках).",
                                REQ,
                                n,
                                n));
                if (issues.size() >= MAX_ISSUES) {
                    return issues;
                }
            }
        }

        return issues;
    }

    private record BibEntriesResult(List<Integer> numbers, int fromWordList, int fromTextPattern) {}

    /** Полный текст для поиска {@code [n]}: приоритет {@code fullText}, иначе все абзацы подряд. */
    private static String entireDocumentText(String fullText, List<ParagraphInfo> paragraphs) {
        if (fullText != null && !fullText.isBlank()) {
            return fullText;
        }
        StringBuilder sb = new StringBuilder();
        for (ParagraphInfo p : paragraphs) {
            String t = p.getText();
            if (t != null && !t.isBlank()) {
                sb.append(t).append('\n');
            }
        }
        return sb.toString();
    }

    private static List<String> buildCitationMatrixLines(
            List<ParagraphInfo> paragraphs, String fullText, int headingIndex, int nextHeadingIndex) {
        List<String> lines = new ArrayList<>();
        String entireDoc = entireDocumentText(fullText, paragraphs);
        Set<Integer> cited = extractBracketNumbers(entireDoc);
        BibEntriesResult parsed = parseBibliographyEntries(paragraphs, headingIndex + 1, nextHeadingIndex);
        List<Integer> bibNums = parsed.numbers();

        lines.add(
                "ФТ-20: сверка ссылок [n] по полному тексту документа (ищется подстрока «[n]», в том числе в списке источников).");
        if (cited.isEmpty()) {
            lines.add("В документе не найдено ни одной подстроки вида [число] в квадратных скобках.");
        } else {
            List<Integer> sorted = new ArrayList<>(cited);
            sorted.sort(Integer::compareTo);
            StringBuilder numList = new StringBuilder();
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) {
                    numList.append(", ");
                }
                numList.append('[').append(sorted.get(i)).append(']');
            }
            lines.add("В документе найдены ссылки: " + numList + ".");
        }
        if (bibNums.isEmpty()) {
            lines.add("Список источников (распознанные записи): пусто.");
        } else {
            lines.add("Распознанные записи списка (порядок в разделе): " + bibNums + ".");
            for (int i = 0; i < bibNums.size(); i++) {
                int n = bibNums.get(i);
                boolean found = cited.contains(n);
                lines.add(
                        String.format(
                                Locale.ROOT,
                                "  источник №%d (позиция %d в списке): подстрока [%d] в документе — %s.",
                                n,
                                i + 1,
                                n,
                                found ? "найдена" : "не найдена"));
            }
        }
        return lines;
    }

    private static BibEntriesResult parseBibliographyEntries(List<ParagraphInfo> paragraphs, int start, int end) {
        List<Integer> nums = new ArrayList<>();
        int fromWordList = 0;
        int fromText = 0;
        for (int i = start; i < end; i++) {
            ParagraphInfo p = paragraphs.get(i);
            String t = p.getText();
            if (t == null || t.isBlank()) {
                continue;
            }
            if (isWordNumberedListBibliographyEntry(p)) {
                nums.add(nums.size() + 1);
                fromWordList++;
                continue;
            }
            for (String line : t.split("\\r?\\n")) {
                String lineTrim = line.trim();
                if (lineTrim.isEmpty()) {
                    continue;
                }
                Matcher m = BIB_ENTRY_TEXT_START.matcher(lineTrim);
                if (m.find()) {
                    nums.add(Integer.parseInt(m.group(1)));
                    fromText++;
                    break;
                }
            }
        }
        return new BibEntriesResult(nums, fromWordList, fromText);
    }

    /**
     * Абзац — пункт нумерованного (не маркированного) списка Word, основной уровень.
     */
    private static boolean isWordNumberedListBibliographyEntry(ParagraphInfo p) {
        if (!p.isNumberingListParagraph()) {
            return false;
        }
        if (p.isNumberingListBullet()) {
            return false;
        }
        Integer ilvl = p.getNumberingIlvl();
        return ilvl == null || ilvl == 0;
    }

    private static Set<Integer> extractBracketNumbers(String text) {
        Set<Integer> s = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return s;
        }
        Matcher m = BRACKET_CITE.matcher(text);
        while (m.find()) {
            s.add(Integer.parseInt(m.group(1)));
        }
        return s;
    }

    private static Integer findSectionHeadingIndex(List<ParagraphInfo> paragraphs) {
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (!isBodyOutlineLevel0(p)) {
                continue;
            }
            if (CheckSession.ft20().sectionTitle().equals(normalizeTitle(p.getText()))) {
                return i;
            }
        }
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (CheckSession.ft20().sectionTitle().equals(normalizeTitle(p.getText()))) {
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
}
