package com.example.backend.check;

import com.example.backend.domain.DocumentPageSettings;
import com.example.backend.domain.PageMargins;
import com.example.backend.domain.ParagraphInfo;
import com.example.backend.domain.SectionPageInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ФТ-10: поля страницы — левое 30 мм, правое 10–15 мм, верхнее и нижнее по 20 мм (п. 4.2).
 * <p>
 * В Word поля задаются на секцию ({@code w:sectPr}), а не на отдельную физическую страницу: на всех страницах
 * фрагмента до следующего разрыва секции действуют одни и те же значения полей. Точный номер страницы из .docx
 * без движка вёрстки недоступен — используется оценка по разрывам OOXML ({@link com.example.backend.util.PageLocator})
 * и, для не последней секции, по странице начала следующей секции.
 */
public final class Ft10PageMarginsChecker {

    private static final double LEFT_CM = 3.0;
    private static final double RIGHT_MIN_CM = 1.0;
    private static final double RIGHT_MAX_CM = 1.5;
    private static final double TOP_CM = 2.0;
    private static final double BOTTOM_CM = 2.0;
    private static final double EPS_CM = 0.05;
    private static final int MAX_ISSUES = 80;

    private Ft10PageMarginsChecker() {
    }

    public static List<String> check(
            DocumentPageSettings pageSettings,
            PageMargins documentMargins,
            List<ParagraphInfo> paragraphs,
            List<Integer> sectPrParagraphIndices) {
        List<String> issues = new ArrayList<>();
        if (pageSettings == null || pageSettings.getSections() == null || pageSettings.getSections().isEmpty()) {
            String loc = formatWholeDocumentLocation(paragraphs);
            collectIssuesForMargins(issues, documentMargins, loc);
            return issues;
        }

        List<SectionPageInfo> sections = pageSettings.getSections();
        int n = sections.size();
        int m = sectPrParagraphIndices == null ? 0 : sectPrParagraphIndices.size();
        boolean partitionOk = (n == m || n == m + 1) && m >= n - 1;
        Integer docLastPage = estimateMaxPageInDocument(paragraphs);

        for (int i = 0; i < n && issues.size() < MAX_ISSUES; i++) {
            SectionPageInfo sec = sections.get(i);
            PageMargins mg = sec.getMargins();
            if (mg == null) {
                mg = documentMargins;
            }
            String loc = formatSectionLocation(
                    i, n, partitionOk, paragraphs, sectPrParagraphIndices, docLastPage);
            collectIssuesForMargins(issues, mg, loc);
        }
        return issues;
    }

    /** Верхняя оценка номера последней страницы документа по всем абзацам (PageLocator). */
    private static Integer estimateMaxPageInDocument(List<ParagraphInfo> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return null;
        }
        int max = Integer.MIN_VALUE;
        for (ParagraphInfo p : paragraphs) {
            Integer pe = p.getPageEndIndex();
            if (pe != null) {
                max = Math.max(max, pe);
            } else if (p.getPageIndex() != null) {
                max = Math.max(max, p.getPageIndex());
            }
        }
        return max == Integer.MIN_VALUE ? null : max;
    }

    private static String formatWholeDocumentLocation(List<ParagraphInfo> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return "документ (поля страницы, п. 4.2)";
        }
        int minPage = Integer.MAX_VALUE;
        int maxPage = Integer.MIN_VALUE;
        for (ParagraphInfo p : paragraphs) {
            Integer pi = p.getPageIndex();
            if (pi != null) {
                minPage = Math.min(minPage, pi);
                maxPage = Math.max(maxPage, pi);
            }
            Integer pe = p.getPageEndIndex();
            if (pe != null) {
                maxPage = Math.max(maxPage, pe);
            }
        }
        if (minPage == Integer.MAX_VALUE) {
            return "документ (поля страницы, п. 4.2)";
        }
        String pagePart = (minPage == maxPage)
                ? String.format(Locale.ROOT,
                "оценка страниц по разрывам OOXML: стр. %d (диапазон может быть шире, если в файле нет сохранённой вёрстки)",
                minPage)
                : String.format(Locale.ROOT, "оценка страниц по разрывам OOXML: стр. %d–%d", minPage, maxPage);
        return String.format(Locale.ROOT,
                "все страницы документа (%s; единая секция полей; п. 4.2)",
                pagePart);
    }

    private static String formatSectionLocation(
            int i,
            int nSections,
            boolean partitionOk,
            List<ParagraphInfo> paragraphs,
            List<Integer> sectPrParagraphIndices,
            Integer docLastPage) {
        if (!partitionOk || paragraphs == null || paragraphs.isEmpty()) {
            return String.format(Locale.ROOT, "секция %d из %d (поля страницы, п. 4.2)", i, nSections);
        }
        if (i > 0 && i - 1 >= sectPrParagraphIndices.size()) {
            return String.format(Locale.ROOT, "секция %d из %d (поля страницы, п. 4.2)", i, nSections);
        }
        int start = (i == 0) ? 0 : sectPrParagraphIndices.get(i - 1) + 1;
        int end = (i == nSections - 1) ? paragraphs.size() - 1 : sectPrParagraphIndices.get(i);
        if (start > end || start < 0 || end >= paragraphs.size()) {
            return String.format(Locale.ROOT, "секция %d из %d (поля страницы, п. 4.2)", i, nSections);
        }
        int minPage = Integer.MAX_VALUE;
        int maxPage = Integer.MIN_VALUE;
        for (int j = start; j <= end; j++) {
            ParagraphInfo p = paragraphs.get(j);
            Integer pi = p.getPageIndex();
            if (pi != null) {
                minPage = Math.min(minPage, pi);
                maxPage = Math.max(maxPage, pi);
            }
            Integer pe = p.getPageEndIndex();
            if (pe != null) {
                maxPage = Math.max(maxPage, pe);
            }
        }
        if (minPage == Integer.MAX_VALUE) {
            return String.format(Locale.ROOT, "секция %d из %d (поля страницы, п. 4.2)", i, nSections);
        }

        if (i < nSections - 1 && i < sectPrParagraphIndices.size()) {
            int firstParaNext = sectPrParagraphIndices.get(i) + 1;
            if (firstParaNext < paragraphs.size()) {
                Integer pNext = paragraphs.get(firstParaNext).getPageIndex();
                if (pNext != null) {
                    if (maxPage < pNext - 1) {
                        maxPage = pNext - 1;
                    }
                    maxPage = Math.min(maxPage, pNext);
                }
            }
        }
        if (i == nSections - 1 && docLastPage != null && docLastPage > maxPage) {
            maxPage = docLastPage;
        }
        if (maxPage < minPage) {
            maxPage = minPage;
        }

        String paraRange = (start == end)
                ? String.format(Locale.ROOT, "абз. #%d", start)
                : String.format(Locale.ROOT, "абз. #%d–#%d", start, end);
        String pageRangeText;
        if (minPage == maxPage) {
            pageRangeText = String.format(Locale.ROOT,
                    "оценка страниц: стр. %d (если одна цифра — в XML часто нет разметки страниц; поля действуют на всём фрагменте секции до следующего разрыва)",
                    minPage);
        } else {
            pageRangeText = String.format(Locale.ROOT, "оценка страниц: стр. %d–%d", minPage, maxPage);
        }
        return String.format(Locale.ROOT,
                "секция %d из %d — поля заданы на секцию (одинаковы на каждой странице фрагмента до следующего разрыва); %s; %s (п. 4.2)",
                i, nSections, paraRange, pageRangeText);
    }

    private static void collectIssuesForMargins(List<String> issues, PageMargins m, String location) {
        if (issues.size() >= MAX_ISSUES) {
            return;
        }
        if (m == null) {
            issues.add(String.format(Locale.ROOT,
                    "ФТ-10: %s — поля страницы не заданы (нет w:pgMar в соответствующей секции и нет запасных полей тела документа).",
                    location));
            return;
        }
        String s = checkLeft(m.getLeftCm());
        if (s != null && issues.size() < MAX_ISSUES) {
            issues.add(String.format(Locale.ROOT, "ФТ-10: %s — %s", location, s));
        }
        s = checkRight(m.getRightCm());
        if (s != null && issues.size() < MAX_ISSUES) {
            issues.add(String.format(Locale.ROOT, "ФТ-10: %s — %s", location, s));
        }
        s = checkTop(m.getTopCm());
        if (s != null && issues.size() < MAX_ISSUES) {
            issues.add(String.format(Locale.ROOT, "ФТ-10: %s — %s", location, s));
        }
        s = checkBottom(m.getBottomCm());
        if (s != null && issues.size() < MAX_ISSUES) {
            issues.add(String.format(Locale.ROOT, "ФТ-10: %s — %s", location, s));
        }
    }

    private static String checkLeft(Double cm) {
        if (cm == null) {
            return "левое поле — нет данных в документе; ожидается 30 мм (п. 4.2).";
        }
        if (Math.abs(cm - LEFT_CM) > EPS_CM) {
            return String.format(Locale.ROOT,
                    "левое поле (п. 4.2) — ожидается 30 мм; фактически %.1f мм (%.2f см).",
                    cm * 10.0, cm);
        }
        return null;
    }

    private static String checkRight(Double cm) {
        if (cm == null) {
            return "правое поле — нет данных в документе; ожидается 10–15 мм (п. 4.2).";
        }
        if (cm < RIGHT_MIN_CM - EPS_CM || cm > RIGHT_MAX_CM + EPS_CM) {
            return String.format(Locale.ROOT,
                    "правое поле (п. 4.2) — ожидается 10–15 мм; фактически %.1f мм (%.2f см).",
                    cm * 10.0, cm);
        }
        return null;
    }

    private static String checkTop(Double cm) {
        if (cm == null) {
            return "верхнее поле — нет данных в документе; ожидается 20 мм (п. 4.2).";
        }
        if (Math.abs(cm - TOP_CM) > EPS_CM) {
            return String.format(Locale.ROOT,
                    "верхнее поле (п. 4.2) — ожидается 20 мм; фактически %.1f мм (%.2f см).",
                    cm * 10.0, cm);
        }
        return null;
    }

    private static String checkBottom(Double cm) {
        if (cm == null) {
            return "нижнее поле — нет данных в документе; ожидается 20 мм (п. 4.2).";
        }
        if (Math.abs(cm - BOTTOM_CM) > EPS_CM) {
            return String.format(Locale.ROOT,
                    "нижнее поле (п. 4.2) — ожидается 20 мм; фактически %.1f мм (%.2f см).",
                    cm * 10.0, cm);
        }
        return null;
    }
}
