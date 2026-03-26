package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ФТ-15: оформление приложений (п. 4.11.2).
 * <p>
 * Каждое приложение начинается с новой страницы (контролируется ФТ-6 по заголовкам «ПРИЛОЖЕНИЕ …»).
 * Для <strong>каждого</strong> заголовка вида «ПРИЛОЖЕНИЕ X» проверяем: наверху страницы (первый непустой абзац
 * на этой странице), выравнивание по центру, запись заголовка <strong>прописными (заглавными) буквами</strong>
 * и порядок букв подряд (первое — А, второе — Б, …) по
 * {@link #APPENDIX_LETTER_ORDER}; латиница A–Z сопоставляется позициям 0..25 той же цепочки.
 * <p>
 * Абзацы <strong>оглавления</strong> исключаются через {@link Ft7TocChecker#indicesOfParagraphsInTocSection}
 * (блок ФТ-7 + все строки после «СОДЕРЖАНИЕ» до первого заголовка раздела), плюс запасные эвристики
 * (стили TOC1–TOC3, «… отточие/таб/пробел … номер страницы»).
 */
public final class Ft15AppendixChecker {

    private static final String REQ = "п. 4.11.2 — оформление приложений";

    private static final int MAX_ISSUES = 60;

    /**
     * Принятая последовательность букв для приложений (кириллица, без Ё; как в типовых ВКР).
     * Латиница A–Z сопоставляется позициям 0..25 той же последовательности.
     */
    private static final String APPENDIX_LETTER_ORDER =
            "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ";

    /**
     * Без {@code \\b} после буквы: граница слова в Java для кириллицы даёт ложные отрицания.
     */
    private static final Pattern ANY_APPENDIX_HEADING = Pattern.compile(
            "^ПРИЛОЖЕНИЕ\\s+[А-ЯA-Z].*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern LETTER_AFTER_PRIL = Pattern.compile(
            "^ПРИЛОЖЕНИЕ\\s+([А-ЯA-Z])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Как в {@link Ft7TocChecker}: строка вида «заголовок … отточие или таб … номер страницы». */
    private static final Pattern TOC_LINE_WITH_LEADER = Pattern.compile(
            "^(?<title>.+?)(?<leader>\\s*[\\.…\u2026·﹒\u00B7]{2,}|\\t+)\\s*(?<page>\\d{1,4})\\s*$",
            Pattern.UNICODE_CASE | Pattern.DOTALL);

    /** Как в {@link Ft7TocChecker}: «заголовок пробел(ы) номер страницы» в конце строки. */
    private static final Pattern TOC_LINE_TAIL_PAGE = Pattern.compile(
            "^(?<title>.+?)\\s+(?<page>\\d{1,4})\\s*$",
            Pattern.UNICODE_CASE | Pattern.DOTALL);

    private static final Pattern TOC_STYLE_ID = Pattern.compile("(?i)^TOC(\\d+)$");

    private Ft15AppendixChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return issues;
        }

        Set<Integer> tocParagraphIndices = Ft7TocChecker.indicesOfParagraphsInTocSection(paragraphs);

        List<Integer> appendixHeadingIndices = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            String norm = normalizeTitle(p.getText());
            if (!norm.isEmpty() && ANY_APPENDIX_HEADING.matcher(norm).matches()) {
                if (tocParagraphIndices.contains(i) || isLikelyTocEntryLine(p)) {
                    continue;
                }
                appendixHeadingIndices.add(i);
            }
        }

        if (appendixHeadingIndices.isEmpty()) {
            return issues;
        }

        for (int k = 0; k < appendixHeadingIndices.size(); k++) {
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            int idx = appendixHeadingIndices.get(k);
            ParagraphInfo p = paragraphs.get(idx);
            String label = appendixHeadingLabel(p.getText());
            String norm = normalizeTitle(p.getText());

            Integer page = p.getPageIndex();
            if (page != null) {
                for (int j = 0; j < idx; j++) {
                    ParagraphInfo prev = paragraphs.get(j);
                    if (prev.getPageIndex() != null
                            && prev.getPageIndex().equals(page)
                            && hasText(prev.getText())) {
                        add(issues,
                                String.format(
                                        Locale.ROOT,
                                        "ФТ-15: %s — заголовок «%s» (абзац на стр. %d) должен быть наверху страницы: "
                                                + "выше на этой же стр. есть непустой абзац #%d с текстом «%s».",
                                        REQ,
                                        label,
                                        page,
                                        j,
                                        shorten(prev.getText(), 80)));
                        break;
                    }
                }
            }

            if (issues.size() >= MAX_ISSUES) {
                break;
            }

            String al = p.getAlignment();
            if (al == null || !"CENTER".equalsIgnoreCase(al)) {
                add(issues,
                        String.format(
                                Locale.ROOT,
                                "ФТ-15: %s — заголовок «%s» (абзац на стр. %s) должен быть выровнен по центру страницы "
                                        + "(по разметке OOXML w:jc у абзаца: %s).",
                                REQ,
                                label,
                                formatIssuePage(page),
                                al == null ? "не задано" : al));
            }

            if (issues.size() >= MAX_ISSUES) {
                break;
            }

            if (!isAppendixHeadingAllUppercase(p.getText())) {
                add(issues,
                        String.format(
                                Locale.ROOT,
                                "ФТ-15: %s — заголовок «%s» (абзац на стр. %s) должен быть набран прописными (заглавными) буквами.",
                                REQ,
                                label,
                                formatIssuePage(page)));
            }

            if (issues.size() >= MAX_ISSUES) {
                break;
            }

            if (k >= APPENDIX_LETTER_ORDER.length()) {
                add(issues,
                        String.format(
                                Locale.ROOT,
                                "ФТ-15: %s — заголовок «%s» (абзац на стр. %s): слишком много приложений подряд "
                                        + "(поддерживается не более %d по порядку букв А, Б, В, …).",
                                REQ,
                                label,
                                formatIssuePage(page),
                                APPENDIX_LETTER_ORDER.length()));
                continue;
            }

            Character letterChar = extractAppendixLetter(norm);
            if (letterChar == null) {
                add(issues,
                        String.format(
                                Locale.ROOT,
                                "ФТ-15: %s — в заголовке «%s» (абзац на стр. %s) не удалось определить букву приложения.",
                                REQ,
                                label,
                                formatIssuePage(page)));
                continue;
            }

            int letterOrdinal = appendixLetterOrdinal(letterChar);
            if (letterOrdinal < 0) {
                add(issues,
                        String.format(
                                Locale.ROOT,
                                "ФТ-15: %s — в заголовке «%s» (абзац на стр. %s) недопустимая буква приложения «%s» "
                                        + "(допустимы буквы по порядку: А, Б, В, …).",
                                REQ,
                                label,
                                formatIssuePage(page),
                                letterChar));
                continue;
            }

            if (letterOrdinal != k) {
                char expected = APPENDIX_LETTER_ORDER.charAt(k);
                add(issues,
                        String.format(
                                Locale.ROOT,
                                "ФТ-15: %s — заголовок «%s» (абзац на стр. %s): приложения должны идти по порядку букв подряд (А, Б, В, …); "
                                        + "для %d-го по счёту приложения ожидается буква «%s», в заголовке — «%s».",
                                REQ,
                                label,
                                formatIssuePage(page),
                                k + 1,
                                expected,
                                letterChar));
            }
        }

        return issues;
    }

    private static Character extractAppendixLetter(String norm) {
        Matcher m = LETTER_AFTER_PRIL.matcher(norm);
        if (!m.find()) {
            return null;
        }
        return m.group(1).charAt(0);
    }

    /**
     * Позиция буквы в {@link #APPENDIX_LETTER_ORDER}: 0 = А, 1 = Б, …; для латиницы A–Z — 0..25 по той же нумерации.
     */
    private static int appendixLetterOrdinal(char c) {
        if (c >= 'A' && c <= 'Z') {
            int i = c - 'A';
            return i < APPENDIX_LETTER_ORDER.length() ? i : -1;
        }
        return APPENDIX_LETTER_ORDER.indexOf(c);
    }

    /** Короткая подпись заголовка для сообщения (как в документе). */
    private static String appendixHeadingLabel(String raw) {
        if (raw == null) {
            return "ПРИЛОЖЕНИЕ";
        }
        String t = raw.replace('\u00A0', ' ').trim();
        return t.isEmpty() ? "ПРИЛОЖЕНИЕ" : shorten(t, 100);
    }

    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /** Все буквы в строке — заглавные (прописные); цифры, пробелы, табы и прочие небуквы не проверяются. */
    private static boolean isAppendixHeadingAllUppercase(String raw) {
        if (raw == null || raw.isEmpty()) {
            return true;
        }
        String s = raw.replace('\u00A0', ' ').replace("\u200B", "");
        return s.codePoints().noneMatch(cp -> Character.isLetter(cp) && Character.isLowerCase(cp));
    }

    /**
     * Дополнительный фильтр, если индекс ещё не в {@link Ft7TocChecker#indicesOfParagraphsInTocSection}.
     */
    private static boolean isLikelyTocEntryLine(ParagraphInfo p) {
        String sid = p.getStyleId();
        if (sid != null && TOC_STYLE_ID.matcher(sid.trim()).matches()) {
            return true;
        }
        String raw = p.getText();
        if (raw == null) {
            return false;
        }
        String trimmed = raw.replace('\u00A0', ' ').replace("\u200B", "").trim();
        if (TOC_LINE_WITH_LEADER.matcher(trimmed).matches()) {
            return true;
        }
        Matcher m = TOC_LINE_TAIL_PAGE.matcher(trimmed);
        if (!m.matches()) {
            return false;
        }
        String title = normalizeTitle(m.group("title"));
        return ANY_APPENDIX_HEADING.matcher(title).matches();
    }

    private static String formatIssuePage(Integer page) {
        return page == null ? "не определена" : page.toString();
    }

    private static String normalizeTitle(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').trim().toUpperCase(Locale.ROOT);
    }

    private static void add(List<String> issues, String msg) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(msg);
        }
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
