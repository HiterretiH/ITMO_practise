package com.example.backend.check;

import com.example.backend.domain.FigureInfo;
import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ФТ-13: оформление иллюстраций (рисунков), п. 4.5.3.
 * <p>
 * Подпись: «Рисунок N – Название рисунка»; расположение — под рисунком, по центру.
 */
public final class Ft13FigureCaptionChecker {

    private static final String REQ = "п. 4.5.3 — оформление иллюстраций (рисунков)";

    private static final int MAX_ISSUES = 60;

    /**
     * «Рисунок», номер, тире/дефис, название (хотя бы один непробельный символ после тире).
     */
    private static final Pattern CAPTION_FORMAT = Pattern.compile(
            "^\\s*рисунок\\s+\\d+\\s*[\\u2013\\u2014\\-]\\s*\\S.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** После «Рисунок» идёт не цифра (буква-заглушка вроде «j», кириллица и т.п.). */
    private static final Pattern NON_DIGIT_AFTER_RISUNOK = Pattern.compile(
            "(?i)рисунок\\s+\\D");

    private Ft13FigureCaptionChecker() {
    }

    public static List<String> check(List<FigureInfo> figures, List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        if (figures == null || figures.isEmpty()) {
            return issues;
        }
        List<ParagraphInfo> paras = paragraphs == null ? List.of() : paragraphs;
        int n = 0;
        for (int i = 0; i < figures.size() && n < MAX_ISSUES; i++) {
            FigureInfo f = figures.get(i);
            String cap = f.getCaption();
            int imgIdx = f.getParagraphIndex();
            int figOrdinal = i + 1;
            String where = whereClause(f, figOrdinal, imgIdx);

            if (cap == null || cap.isBlank()) {
                add(issues,
                        "ФТ-13: " + REQ + " — " + where + ": нет подписи под рисунком. Нужно: «Рисунок N – Название» "
                                + "(N — номер арабскими цифрами), подпись по центру под рисунком.");
                n++;
                continue;
            }

            String normalized = normalizeCaption(cap);
            if (!CAPTION_FORMAT.matcher(normalized).matches()) {
                add(issues,
                        "ФТ-13: " + REQ + " — " + where + ": подпись не по шаблону «Рисунок N – Название рисунка». "
                                + "Найдено: «" + shorten(cap, 120) + "». "
                                + explainFormatMismatch(cap));
                n++;
                continue;
            }

            Integer capIdx = f.getCaptionParagraphIndex();
            if (capIdx == null) {
                capIdx = findParagraphIndexByExactText(paras, cap);
            }
            if (capIdx == null) {
                add(issues,
                        "ФТ-13: " + REQ + " — " + where + ": подпись по формату есть, но абзац с этим текстом "
                                + "не найден в модели документа — выравнивание по центру автоматически не проверено "
                                + "(убедитесь, что подпись — отдельный абзац под рисунком, по центру).");
                n++;
                continue;
            }
            if (capIdx < 0 || capIdx >= paras.size()) {
                add(issues,
                        "ФТ-13: " + REQ + " — " + where + ": индекс абзаца подписи (#"
                                + capIdx + ") вне диапазона абзацев документа — выравнивание не проверено.");
                n++;
                continue;
            }
            if (capIdx < imgIdx) {
                add(issues,
                        "ФТ-13: " + REQ + " — " + where + ": подпись должна быть под рисунком (абзац подписи #"
                                + capIdx + " выше абзаца с рисунком #" + imgIdx + ").");
                n++;
            }
            if (n >= MAX_ISSUES) {
                break;
            }
            String al = paras.get(capIdx).getAlignment();
            if (al == null || !"CENTER".equalsIgnoreCase(al)) {
                add(issues,
                        "ФТ-13: " + REQ + " — " + where + ": подпись должна быть выровнена по центру "
                                + "(абзац подписи #" + capIdx + "; сейчас: "
                                + (al == null ? "не задано" : al) + ").");
                n++;
            }
        }
        return issues;
    }

    /**
     * Где искать: страница (оценка), порядковый номер рисунка, абзац с картинкой.
     */
    private static String whereClause(FigureInfo f, int figureOrdinal1Based, int imageParagraphIndex) {
        StringBuilder sb = new StringBuilder();
        int pg = f.getPageIndex();
        if (pg > 0) {
            sb.append("стр. ").append(pg).append(" (оценка по разрывам OOXML), ");
        } else {
            sb.append("стр. не определена, ");
        }
        sb.append("рисунок ").append(figureOrdinal1Based).append(" по порядку, абзац с рисунком #").append(imageParagraphIndex);
        return sb.toString();
    }

    private static String explainFormatMismatch(String rawCaption) {
        if (rawCaption == null) {
            return "";
        }
        String base = "Что не так: после «Рисунок» должен идти номер арабскими цифрами (1, 2, 3, …), затем тире и название.";
        if (NON_DIGIT_AFTER_RISUNOK.matcher(normalizeCaption(rawCaption)).find()) {
            return base + " Сейчас вместо цифр — буква или другой символ (часто оставляют «j» как заглушку).";
        }
        return base + " Проверьте тире между номером и текстом и отсутствие лишних символов в начале.";
    }

    private static String normalizeCaption(String text) {
        return text
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .trim();
    }

    private static Integer findParagraphIndexByExactText(List<ParagraphInfo> paragraphs, String caption) {
        if (caption == null) {
            return null;
        }
        String want = caption.trim();
        for (int i = 0; i < paragraphs.size(); i++) {
            String t = paragraphs.get(i).getText();
            if (t != null && t.trim().equals(want)) {
                return i;
            }
        }
        return null;
    }

    private static void add(List<String> issues, String msg) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(msg);
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }
}
