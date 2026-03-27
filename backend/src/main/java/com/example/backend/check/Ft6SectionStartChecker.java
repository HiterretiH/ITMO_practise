package com.example.backend.check;

import com.example.backend.config.checks.CheckSession;
import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ФТ-6: обязательные заголовки и главы основной части должны начинаться с новой страницы (п. 4.4.2).
 * <p>
 * Эвристика: начало абзаца-заголовка должно быть на странице <strong>после</strong> конца предыдущего
 * непустого абзаца ({@code pageIndex > prev.pageEndIndex}).
 * <p>
 * <b>Когда проверка «молчит» (0 замечаний при реальной ошибке):</b> номера страниц берутся только из OOXML
 * ({@code w:lastRenderedPageBreak}, явный разрыв страницы, разрыв секции «следующая страница»).
 * Если документ сохранён без пересчёта вёрстки в Word, в файле нет {@code lastRenderedPageBreak}, все абзацы
 * окажутся на «стр. 1» — ФТ-6 не увидит нарушение. Пример: убрать разрыв перед «ВВЕДЕНИЕ», сохранить как
 * «только XML» или в редакторе без обновления полей — парсер не узнает реальную разбивку на страницы.
 */
public final class Ft6SectionStartChecker {

    private static final Pattern APPENDIX_HEADING = Pattern.compile(
            "^ПРИЛОЖЕНИЕ\\s+[А-ЯA-Z]\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private Ft6SectionStartChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (!isFt6Target(p)) {
                continue;
            }
            int prevIdx = previousNonEmptyIndex(paragraphs, i);
            if (prevIdx < 0) {
                continue;
            }
            ParagraphInfo prev = paragraphs.get(prevIdx);
            Integer pStart = p.getPageIndex();
            Integer prevEnd = prev.getPageEndIndex();
            if (pStart == null || prevEnd == null) {
                continue;
            }
            if (pStart <= prevEnd) {
                String label = shorten(p.getText(), 80);
                issues.add(String.format(
                        Locale.ROOT,
                        "ФТ-6: блок «%s» должен начинаться с новой страницы: начало на стр. %d, предыдущий абзац заканчивается на стр. %d.",
                        label,
                        pStart,
                        prevEnd
                ));
            }
        }
        return issues;
    }

    static boolean isFt6Target(ParagraphInfo p) {
        String t = normalizeTitle(p.getText());
        if (t.isEmpty()) {
            return false;
        }
        if (CheckSession.ft6().fixedSectionTitles().contains(t)) {
            return true;
        }
        if (APPENDIX_HEADING.matcher(t).matches()) {
            return true;
        }
        return isMainChapterHeading(p, t);
    }

    /**
     * Глава основной части (например «1. НАЗВАНИЕ»), без подразделов «1.1 …».
     */
    private static boolean isMainChapterHeading(ParagraphInfo p, String t) {
        if (t.matches("^\\d+\\.\\d+.*")) {
            return false;
        }
        boolean looksLikeChapter = t.matches("^\\d+\\.\\s+\\p{Lu}.*") || t.matches("^\\d+\\s+\\p{Lu}.*");
        if (!looksLikeChapter) {
            return false;
        }
        return p.getOutlineLevel() != null && p.getOutlineLevel() == 0;
    }

    private static String normalizeTitle(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').trim().toUpperCase(Locale.ROOT);
    }

    private static int previousNonEmptyIndex(List<ParagraphInfo> paragraphs, int current) {
        for (int j = current - 1; j >= 0; j--) {
            String tx = paragraphs.get(j).getText();
            if (tx != null && !tx.trim().isEmpty()) {
                return j;
            }
        }
        return -1;
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
