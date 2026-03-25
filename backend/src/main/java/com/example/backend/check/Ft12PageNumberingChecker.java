package com.example.backend.check;

import com.example.backend.model.domain.DocumentPageSettings;
import com.example.backend.model.domain.PageNumberingInfo;
import com.example.backend.model.domain.ParagraphInfo;
import com.example.backend.model.domain.SectionPageInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * ФТ-12: нумерация страниц (п. 4.3.1).
 * <p>
 * <b>Базовые требования (на них делается акцент в сообщениях):</b>
 * <ol>
 *   <li><b>Постоянная сквозная</b> нумерация по всему документу (без перезапуска по секциям).</li>
 *   <li>Номер <b>всегда внизу</b> страницы — поле PAGE в нижнем колонтитуле (не в верхнем).</li>
 *   <li><b>По центру</b> внизу.</li>
 *   <li><b>Без точки</b> после номера (проверка по разметке OOXML).</li>
 * </ol>
 * Дополнительно: отдельные подвалы первой/чётных страниц без PAGE и стартовый номер меньше единицы —
 * они нарушают ту же схему «одинаково снизу по центру на всех страницах».
 */
public final class Ft12PageNumberingChecker {

    /**
     * Единая формулировка требований п. 4.3.1 — во всех сообщениях, чтобы акцент был явным.
     */
    private static final String REQ =
            "п. 4.3.1 — постоянная сквозная нумерация, внизу по центру, без точки после номера";

    private static final int MAX_ISSUES = 40;
    /** Не перечислять каждую чётную страницу, если их слишком много. */
    private static final int MAX_EVEN_PAGES_LISTED = 24;

    private Ft12PageNumberingChecker() {
    }

    public static List<String> check(DocumentPageSettings pageSettings) {
        return check(pageSettings, List.of());
    }

    public static List<String> check(DocumentPageSettings pageSettings, List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        if (pageSettings == null) {
            add(issues, "ФТ-12: " + REQ + " — нет данных о страницах и колонтитулах.");
            return issues;
        }
        PageNumberingInfo n = pageSettings.getNumbering();
        if (n == null) {
            add(issues, "ФТ-12: " + REQ + " — нет сведений о нумерации в документе.");
            return issues;
        }

        boolean hasFooterPage = n.isFooterPageFieldPresent();
        boolean hasHeaderPage = n.isHeaderPageFieldPresent();

        // 1) Внизу + поле PAGE (не вверху)
        if (!hasFooterPage) {
            if (hasHeaderPage) {
                add(issues,
                        "ФТ-12: " + REQ + " — номер должен быть внизу по центру; поле PAGE найдено только в "
                                + "верхнем колонтитуле, в нижнем не найдено.");
            } else {
                add(issues,
                        "ФТ-12: " + REQ + " — в нижнем колонтитуле не найдено поле PAGE (нумерация должна быть снизу).");
            }
        }

        // 2) По центру внизу
        if (hasFooterPage && Boolean.FALSE.equals(n.getFooterPageParagraphCentered())) {
            add(issues,
                    "ФТ-12: " + REQ + " — абзац с полем PAGE в нижнем колонтитуле не выровнен по центру "
                            + "(нужно по центру внизу).");
        }

        // 3) Без точки после номера
        if (n.isFooterTrailingPeriodAfterPageSuspected()) {
            add(issues,
                    "ФТ-12: " + REQ + " — в подвале после поля номера обнаружена отдельная точка "
                            + "(после номера точка не допускается; проверка по разметке OOXML).");
        }

        // 4) Постоянная сквозная нумерация (не перезапуск по секциям)
        if (n.isPageNumberRestartInSections()) {
            add(issues,
                    "ФТ-12: " + REQ + " — задан перезапуск нумерации страниц в секции "
                            + "(нужна непрерывная сквозная нумерация по всему документу, включая приложения).");
        }

        List<ParagraphInfo> paras = paragraphs == null ? List.of() : paragraphs;
        int maxPage = computeMaxPageIndex(paras);

        List<SectionPageInfo> sections = pageSettings.getSections();
        Integer declaredStart = null;
        if (sections != null && !sections.isEmpty()) {
            declaredStart = sections.get(0).getPageNumberStart();
        }

        if (declaredStart != null && declaredStart < 1) {
            add(issues,
                    "ФТ-12: " + REQ + " — в первой секции задан стартовый номер страницы < 1 "
                            + "(допустимы только целые числа больше нуля).");
        }

        if (n.isFirstPageFooterPresent() && !n.isFirstPageFooterHasPageField()) {
            add(issues,
                    "ФТ-12: " + REQ + " — включён отдельный подвал первой страницы без поля PAGE "
                            + "(сквозная нумерация снизу по центру должна повторяться на всех страницах, включая первую).");
        }

        if (n.isEvenPageFooterPresent() && !n.isEvenPageFooterHasPageField()) {
            if (maxPage >= 2) {
                String range = formatEvenPagesRange(maxPage);
                add(issues,
                        "ФТ-12: " + REQ + " — отдельный подвал чётных страниц без поля PAGE "
                                + "(стр. " + range + "): сквозная нумерация снизу по центру должна быть на всех страницах.");
            } else {
                add(issues,
                        "ФТ-12: " + REQ + " — отдельный подвал чётных страниц без поля PAGE "
                                + "(сквозная нумерация снизу по центру должна быть и на чётных страницах).");
            }
        }

        return issues;
    }

    /**
     * Максимальный индекс страницы по абзацам (1-based), 0 — если оценки нет.
     */
    private static int computeMaxPageIndex(List<ParagraphInfo> paragraphs) {
        int max = 0;
        for (ParagraphInfo p : paragraphs) {
            if (p == null) {
                continue;
            }
            if (p.getPageEndIndex() != null) {
                max = Math.max(max, p.getPageEndIndex());
            }
            if (p.getPageIndex() != null) {
                max = Math.max(max, p.getPageIndex());
            }
        }
        return max;
    }

    /** Список чётных номеров страниц от 2 до maxPage включительно, с сокращением при длинном списке. */
    private static String formatEvenPagesRange(int maxPage) {
        if (maxPage < 2) {
            return "2";
        }
        int count = maxPage / 2;
        if (count <= MAX_EVEN_PAGES_LISTED) {
            StringBuilder sb = new StringBuilder();
            for (int p = 2; p <= maxPage; p += 2) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(p);
            }
            return sb.toString();
        }
        return "2, 4, 6, …, " + (maxPage % 2 == 0 ? maxPage : maxPage - 1);
    }

    private static void add(List<String> issues, String line) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(line);
        }
    }
}
