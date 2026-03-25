package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ФТ-4: наличие обязательных разделов (п. 3.2), заголовки прописными (п. 4.4.1).
 * <p>
 * Учитываются заголовки уровня 0 в теле документа (не строки оглавления в блоке TOC без outline):
 * у фиксированных разделов в оглавлении часто {@code outlineLvl=null}, у абзаца в тексте — стиль главы с
 * {@code outlineLvl=0}.
 */
public final class Ft4RequiredSectionsChecker {

    private static final Set<String> FIXED_SECTION_TITLES = Set.of(
            "СОДЕРЖАНИЕ",
            "ВВЕДЕНИЕ",
            "ЗАКЛЮЧЕНИЕ",
            "СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ"
    );

    /** Буква приложения (А, Б, …): без \\b в конце — для кириллицы надёжнее. */
    private static final Pattern APPENDIX_HEADING = Pattern.compile(
            "^ПРИЛОЖЕНИЕ\\s+\\S.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** Глава основной части: «1. НАЗВАНИЕ». */
    private static final Pattern MAIN_CHAPTER = Pattern.compile(
            "^\\s*\\d+\\.\\s+\\p{Lu}.*",
            Pattern.UNICODE_CASE
    );

    private Ft4RequiredSectionsChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();

        for (String title : FIXED_SECTION_TITLES) {
            Found f = findBodyOutlineHeading(paragraphs, title);
            if (f == null) {
                issues.add("ФТ-4: в тексте не найден обязательный раздел «" + title + "» (заголовок уровня 0).");
                continue;
            }
            if (!isAllCapsLine(f.rawText())) {
                issues.add("ФТ-4: заголовок раздела «" + title
                        + "» должен быть набран прописными буквами (п. 4.4.1).");
            }
        }

        boolean appendix = false;
        for (ParagraphInfo p : paragraphs) {
            if (!isBodyOutlineLevel0(p)) {
                continue;
            }
            String t = safeTrim(p.getText());
            if (t.isEmpty()) {
                continue;
            }
            if (APPENDIX_HEADING.matcher(normalizeTitle(t)).matches()) {
                appendix = true;
                if (!isAllCapsLine(t)) {
                    issues.add("ФТ-4: заголовок приложения должен быть прописными буквами (п. 4.4.1): «"
                            + shorten(t, 80) + "».");
                }
            }
        }
        if (!appendix) {
            issues.add("ФТ-4: не найдено ни одного раздела «ПРИЛОЖЕНИЕ …» (заголовок уровня 0).");
        }

        boolean mainChapter = false;
        for (ParagraphInfo p : paragraphs) {
            if (!isBodyOutlineLevel0(p)) {
                continue;
            }
            String t = safeTrim(p.getText());
            if (MAIN_CHAPTER.matcher(t).matches()) {
                mainChapter = true;
                break;
            }
        }
        if (!mainChapter) {
            issues.add("ФТ-4: не найдена ни одна глава основной части (заголовок вида «1. НАЗВАНИЕ», уровень 0).");
        }

        return issues;
    }

    private record Found(String rawText) {}

    private static Found findBodyOutlineHeading(List<ParagraphInfo> paragraphs, String titleUpper) {
        for (ParagraphInfo p : paragraphs) {
            if (!isBodyOutlineLevel0(p)) {
                continue;
            }
            String nt = normalizeTitle(p.getText());
            if (nt.equals(titleUpper)) {
                return new Found(safeTrim(p.getText()));
            }
        }
        return null;
    }

    /** Тело: заголовок с привязкой к уровню структуры (отсекаем строки TOC без outline). */
    private static boolean isBodyOutlineLevel0(ParagraphInfo p) {
        Integer ol = p.getOutlineLevel();
        return ol != null && ol == 0;
    }

    private static boolean isAllCapsLine(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String t = text.replace('\u00A0', ' ').trim();
        if (t.isEmpty()) {
            return false;
        }
        return t.equals(t.toUpperCase(Locale.ROOT));
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

    private static String safeTrim(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').trim();
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
