package com.example.backend.check;

import com.example.backend.model.domain.DocumentPageSettings;
import com.example.backend.model.domain.PageNumberingInfo;
import com.example.backend.model.domain.ParagraphInfo;
import com.example.backend.model.domain.SectionPageInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ФТ-12: нумерация страниц (п. 4.3.1).
 * <p>
 * Базовые требования в сообщениях: постоянная сквозная нумерация, внизу по центру, без точки после номера.
 * Для несоответствий по w:start / перезапускам выводится ожидаемый номер на физической странице (модель по OOXML,
 * без отрисовки Word — как и для PAGE в подвале).
 */
public final class Ft12PageNumberingChecker {

    private static final String REQ =
            "п. 4.3.1 — постоянная сквозная нумерация, внизу по центру, без точки после номера";

    /** Общий лимит строк-замечаний. */
    private static final int MAX_ISSUES = 60;
    private static final int MAX_EVEN_PAGES_LISTED = 24;
    /** Сколько физ. страниц перечислить в одном сообщении про сдвиг нумерации. */
    private static final int MAX_PAGES_LISTED_IN_SHIFT = 22;

    private Ft12PageNumberingChecker() {
    }

    public static List<String> check(DocumentPageSettings pageSettings) {
        return check(pageSettings, List.of(), null);
    }

    public static List<String> check(DocumentPageSettings pageSettings, List<ParagraphInfo> paragraphs) {
        return check(pageSettings, paragraphs, null);
    }

    /**
     * @param sectPrParagraphIndices индексы абзацев с {@code w:sectPr} (как в {@link com.example.backend.model.domain.DocumentStructure})
     */
    public static List<String> check(
            DocumentPageSettings pageSettings,
            List<ParagraphInfo> paragraphs,
            List<Integer> sectPrParagraphIndices) {
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

        List<ParagraphInfo> paras = paragraphs == null ? List.of() : paragraphs;
        List<Integer> sectIdx = sectPrParagraphIndices == null ? List.of() : sectPrParagraphIndices;
        String pagesHint = formatDocumentPageEstimate(paras);
        int maxPage = computeMaxPageIndex(paras);

        boolean hasFooterPage = n.isFooterPageFieldPresent();
        boolean hasHeaderPage = n.isHeaderPageFieldPresent();

        if (!hasFooterPage) {
            if (hasHeaderPage) {
                add(issues,
                        "ФТ-12: " + REQ + " — номер должен быть внизу по центру; поле PAGE найдено только в "
                                + "верхнем колонтитуле, в нижнем не найдено (" + pagesHint + ").");
            } else {
                add(issues,
                        "ФТ-12: " + REQ + " — в нижнем колонтитуле не найдено поле PAGE (" + pagesHint + ").");
            }
        }

        if (hasFooterPage && Boolean.FALSE.equals(n.getFooterPageParagraphCentered())) {
            add(issues,
                    "ФТ-12: " + REQ + " — абзац с полем PAGE в нижнем колонтитуле не выровнен по центру "
                            + "(нужно по центру внизу; " + pagesHint + ").");
        }

        if (n.isFooterTrailingPeriodAfterPageSuspected()) {
            add(issues,
                    "ФТ-12: " + REQ + " — в подвале после поля номера обнаружена отдельная точка "
                            + "(после номера точка не допускается; " + pagesHint + ").");
        }

        List<SectionPageInfo> sections = pageSettings.getSections();
        Integer declaredStart = null;
        if (sections != null && !sections.isEmpty()) {
            declaredStart = sections.get(0).getPageNumberStart();
        }

        if (declaredStart != null && declaredStart < 1) {
            add(issues,
                    "ФТ-12: " + REQ + " — в первой секции задан стартовый номер страницы < 1 "
                            + "(допустимы только целые числа больше нуля; " + pagesHint + ").");
        }

        if (n.isPageNumberRestartInSections()) {
            String where = describeRestartInSections(sections, paras, sectIdx);
            add(issues,
                    "ФТ-12: " + REQ + " — перезапуск нумерации страниц в секции после первой "
                            + "(нужна непрерывная сквозная нумерация по всему документу). " + where);
        }

        if (sections != null && !sections.isEmpty() && maxPage >= 1) {
            addExpectedVersusActualNumberingMessages(
                    issues,
                    sections,
                    paras,
                    sectIdx,
                    declaredStart,
                    maxPage,
                    pagesHint,
                    n.isPageNumberRestartInSections());
        }

        if (n.isFirstPageFooterPresent() && !n.isFirstPageFooterHasPageField()) {
            add(issues,
                    "ФТ-12: " + REQ + " — отдельный подвал первой страницы без поля PAGE "
                            + "(стр. 1: сквозной номер снизу может отсутствовать; " + pagesHint + ").");
        }

        if (n.isEvenPageFooterPresent() && !n.isEvenPageFooterHasPageField()) {
            if (maxPage >= 2) {
                String range = formatEvenPagesRange(maxPage);
                add(issues,
                        "ФТ-12: " + REQ + " — отдельный подвал чётных страниц без поля PAGE "
                                + "(стр. " + range + "; " + pagesHint + ").");
            } else {
                add(issues,
                        "ФТ-12: " + REQ + " — отдельный подвал чётных страниц без поля PAGE (" + pagesHint + ").");
            }
        }

        return issues;
    }

    /**
     * Ожидаемая сквозная нумерация: на физической странице p в подвале должен быть номер p (первая страница = 1).
     * Сравниваем с тем, что даёт цепочка w:start по секциям (оценка по разметке, без рендера Word).
     */
    private static void addExpectedVersusActualNumberingMessages(
            List<String> issues,
            List<SectionPageInfo> sections,
            List<ParagraphInfo> paragraphs,
            List<Integer> sectPrParagraphIndices,
            Integer declaredStartFirstSection,
            int maxPage,
            String pagesHint,
            boolean pageNumberRestartInSections) {
        int nSec = sections.size();
        int paraCount = paragraphs.size();
        if (paraCount == 0 || nSec <= 0) {
            return;
        }

        int[] firstPhysical = new int[nSec];
        int[] lastPhysical = new int[nSec];
        for (int i = 0; i < nSec; i++) {
            int[] range = paragraphRangeForSection(i, nSec, sectPrParagraphIndices, paraCount);
            int fp = minPageInRange(paragraphs, range[0], range[1]);
            int lp = maxPageInRange(paragraphs, range[0], range[1]);
            if (fp < 1) {
                fp = 1;
            }
            if (lp < fp) {
                lp = fp;
            }
            firstPhysical[i] = fp;
            lastPhysical[i] = lp;
        }
        if (nSec > 1) {
            for (int i = 0; i < nSec - 1; i++) {
                int fpNext = firstPhysical[i + 1];
                if (fpNext > firstPhysical[i]) {
                    lastPhysical[i] = Math.min(lastPhysical[i], fpNext - 1);
                }
            }
        }
        lastPhysical[nSec - 1] = Math.max(lastPhysical[nSec - 1], maxPage);

        int s0 = declaredStartFirstSection != null ? declaredStartFirstSection : 1;
        int fp0 = firstPhysical[0];
        if (fp0 < 1) {
            fp0 = 1;
        }
        int lp0 = Math.min(Math.max(lastPhysical[0], fp0), maxPage);

        long lastDisplayedOnPrevSectionEnd = s0 + (long) (lp0 - fp0);

        for (int si = 1; si < nSec; si++) {
            SectionPageInfo sec = sections.get(si);
            int fp = firstPhysical[si];
            int lp = Math.min(lastPhysical[si], maxPage);
            if (fp > maxPage) {
                continue;
            }
            lp = Math.max(lp, fp);
            boolean restartHere = Boolean.TRUE.equals(sec.getSectionRestartsPageNumbering());
            Integer r = sec.getPageNumberStart();
            long expectedFirst = lastDisplayedOnPrevSectionEnd + 1L;
            if (restartHere && r != null) {
                if (expectedFirst != r) {
                    String fix = String.format(Locale.ROOT,
                            "для сквозной нумерации в подвале на этой странице должно быть %d; "
                                    + "в секции задан перезапуск с %d — уберите w:start или задайте start=%d (%s)",
                            expectedFirst, r, expectedFirst, pagesHint);
                    add(issues,
                            "ФТ-12: " + REQ + " — стр. " + fp + " (начало секции " + si + " из " + nSec + "): " + fix);
                }
                lastDisplayedOnPrevSectionEnd = r + (long) (lp - fp);
            } else {
                int nPages = lp - fp + 1;
                lastDisplayedOnPrevSectionEnd = lastDisplayedOnPrevSectionEnd + (long) nPages;
            }
        }

        long delta = (long) s0 - (long) fp0;
        if (delta == 0) {
            return;
        }

        // Если перезапуска в следующих секциях нет, w:start первой секции задаёт нумерацию на весь документ —
        // не ограничиваемся lp0 по абзацам (иначе остаётся только стр. 1 при длинном документе).
        int upTo = pageNumberRestartInSections ? Math.min(lp0, maxPage) : maxPage;
        if (fp0 > upTo) {
            return;
        }

        StringBuilder perPage = new StringBuilder();
        int listed = 0;
        for (int p = fp0; p <= upTo && listed < MAX_PAGES_LISTED_IN_SHIFT; p++) {
            long willShow = (long) s0 + (long) (p - fp0);
            if (willShow != p) {
                if (perPage.length() > 0) {
                    perPage.append("; ");
                }
                perPage.append(String.format(Locale.ROOT,
                        "стр. %d — по w:start поле PAGE даст %d, при сквозной 1…N нужно %d", p, willShow, p));
                listed++;
            }
        }
        if (perPage.length() == 0) {
            return;
        }
        String tail = "";
        int span = upTo - fp0 + 1;
        if (span > listed && listed >= MAX_PAGES_LISTED_IN_SHIFT) {
            tail = "; … (то же смещение для стр. " + (fp0 + listed) + "–" + upTo + ")";
        }

        String scope = (upTo >= maxPage || nSec == 1)
                ? "документ до стр. " + upTo
                : "первая секция (стр. " + fp0 + "–" + upTo + ")";
        add(issues,
                "ФТ-12: " + REQ + " — непоследовательность номеров (" + scope + "): "
                        + perPage + tail
                        + ". Что сделать: в w:pgNumType первой секции задайте w:start=" + fp0
                        + " (номер первой страницы фрагмента) или w:start=1, если текст начинается со стр. 1; "
                        + "иначе уберите w:start (" + pagesHint + ").");
    }

    private static int[] paragraphRangeForSection(
            int sectionIndex,
            int nSections,
            List<Integer> sectPrParagraphIndices,
            int paraCount) {
        if (paraCount <= 0) {
            return new int[]{0, 0};
        }
        int start = (sectionIndex == 0) ? 0 : sectPrParagraphIndices.get(sectionIndex - 1) + 1;
        int end = (sectionIndex == nSections - 1)
                ? paraCount - 1
                : sectPrParagraphIndices.get(sectionIndex);
        if (start > end) {
            return new int[]{start, start};
        }
        return new int[]{start, end};
    }

    private static int minPageInRange(List<ParagraphInfo> paragraphs, int from, int to) {
        int min = Integer.MAX_VALUE;
        for (int i = from; i <= to && i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (p == null) {
                continue;
            }
            if (p.getPageIndex() != null) {
                min = Math.min(min, p.getPageIndex());
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private static int maxPageInRange(List<ParagraphInfo> paragraphs, int from, int to) {
        int max = 0;
        for (int i = from; i <= to && i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (p == null) {
                continue;
            }
            if (p.getPageIndex() != null) {
                max = Math.max(max, p.getPageIndex());
            }
            if (p.getPageEndIndex() != null) {
                max = Math.max(max, p.getPageEndIndex());
            }
        }
        return max;
    }

    /** Где именно в документе перезапуск (секция, оценка страницы начала фрагмента). */
    private static String describeRestartInSections(
            List<SectionPageInfo> sections,
            List<ParagraphInfo> paragraphs,
            List<Integer> sectPrParagraphIndices) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < sections.size(); i++) {
            SectionPageInfo s = sections.get(i);
            if (!Boolean.TRUE.equals(s.getSectionRestartsPageNumbering())) {
                continue;
            }
            Integer startVal = s.getPageNumberStart();
            int paraStart = startParagraphIndexForSection(i, sectPrParagraphIndices);
            Integer pageAt = pageAtParagraphIndex(paragraphs, paraStart);
            sb.append(String.format(Locale.ROOT,
                    "Секция %d из %d: задан w:start%s; начало фрагмента — %s. ",
                    i,
                    sections.size(),
                    startVal != null ? " (" + startVal + ")" : "",
                    pageAt != null
                            ? "оценка стр. " + pageAt + " (абз. #" + paraStart + ")"
                            : "абз. #" + paraStart + " (оценка страницы по OOXML недоступна)"));
        }
        return sb.toString().trim();
    }

    private static int startParagraphIndexForSection(int sectionIndex, List<Integer> sectPrParagraphIndices) {
        if (sectionIndex <= 0) {
            return 0;
        }
        if (sectPrParagraphIndices == null || sectionIndex - 1 >= sectPrParagraphIndices.size()) {
            return 0;
        }
        return sectPrParagraphIndices.get(sectionIndex - 1) + 1;
    }

    private static Integer pageAtParagraphIndex(List<ParagraphInfo> paragraphs, int paragraphIndex) {
        if (paragraphs == null || paragraphIndex < 0 || paragraphIndex >= paragraphs.size()) {
            return null;
        }
        ParagraphInfo p = paragraphs.get(paragraphIndex);
        if (p.getPageIndex() != null) {
            return p.getPageIndex();
        }
        return null;
    }

    private static String formatDocumentPageEstimate(List<ParagraphInfo> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return "оценка страниц по разрывам OOXML: нет данных";
        }
        int minPage = Integer.MAX_VALUE;
        int maxPage = 0;
        for (ParagraphInfo p : paragraphs) {
            if (p == null) {
                continue;
            }
            if (p.getPageIndex() != null) {
                minPage = Math.min(minPage, p.getPageIndex());
                maxPage = Math.max(maxPage, p.getPageIndex());
            }
            if (p.getPageEndIndex() != null) {
                maxPage = Math.max(maxPage, p.getPageEndIndex());
            }
        }
        if (maxPage <= 0) {
            return "оценка страниц по разрывам OOXML: нет данных";
        }
        if (minPage == Integer.MAX_VALUE) {
            minPage = 1;
        }
        if (minPage == maxPage) {
            return String.format(Locale.ROOT, "оценка стр. %d (по разрывам OOXML)", maxPage);
        }
        return String.format(Locale.ROOT, "оценка стр. %d–%d (по разрывам OOXML)", minPage, maxPage);
    }

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
