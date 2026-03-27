package com.example.backend.check;

import com.example.backend.config.checks.CheckSession;
import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ФТ-16: дополнительные структурные элементы по п. 3.2 (если раздел указан в содержании — он должен быть в тексте):
 * «СПИСОК СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ», «ТЕРМИНЫ И ОПРЕДЕЛЕНИЯ», «СПИСОК ИЛЛЮСТРАТИВНОГО МАТЕРИАЛА».
 * <p>
 * Если блок оглавления не распознан, проверка не выдаёт замечаний (нет данных о том, что «указано в содержании»).
 */
public final class Ft16OptionalStructuralElementsChecker {

    private static final String REQ = "п. 3.2 — дополнительные структурные элементы";

    private Ft16OptionalStructuralElementsChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return issues;
        }
        List<String> tocKeys = Ft7TocChecker.tocLineTitleKeys(paragraphs);
        if (tocKeys.isEmpty()) {
            return issues;
        }

        for (String canonical : CheckSession.ft16().optionalSectionTitles()) {
            String c = normalizeTitle(canonical);
            if (!isListedInToc(tocKeys, c)) {
                continue;
            }
            if (!bodyHasHeadingMatching(paragraphs, c)) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "ФТ-16: %s — в содержании указан раздел «%s», но в тексте не найден заголовок уровня 0 "
                                        + "с соответствующим названием (проверьте написание и стиль заголовка).",
                                REQ,
                                canonical));
            }
        }
        return issues;
    }

    private static boolean isListedInToc(List<String> tocKeys, String canonicalKey) {
        for (String tk : tocKeys) {
            if (Ft7TocChecker.titlesMatchTocToBody(tk, canonicalKey)) {
                return true;
            }
        }
        return false;
    }

    private static boolean bodyHasHeadingMatching(List<ParagraphInfo> paragraphs, String canonicalKey) {
        for (ParagraphInfo p : paragraphs) {
            if (!isBodyOutlineLevel0(p)) {
                continue;
            }
            String bk = normalizeTitle(p.getText());
            if (bk.isEmpty()) {
                continue;
            }
            if (Ft7TocChecker.titlesMatchTocToBody(canonicalKey, bk)) {
                return true;
            }
        }
        return false;
    }

    /** Как {@link Ft4RequiredSectionsChecker}: заголовок раздела в теле, не строка TOC без outline. */
    private static boolean isBodyOutlineLevel0(ParagraphInfo p) {
        Integer ol = p.getOutlineLevel();
        return ol != null && ol == 0;
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
}
