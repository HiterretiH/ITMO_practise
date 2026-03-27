package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import com.example.backend.domain.TableInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * оформление таблиц, п. 4.6.3.
 * <p>
 * Название над таблицей слева: «Таблица N – Название таблицы». Объекты в {@link TableInfo} — настоящие
 * таблицы Word ({@code w:tbl}); вставка таблицы как растрового рисунка в список таблиц не попадает
 * (такая вставка учитывается как изображение, см. ФТ-13).
 */
public final class Ft14TableCaptionChecker {

    private static final String REQ = "п. 4.6.3 — оформление таблиц";

    private static final int MAX_ISSUES = 60;

    private static final Pattern CAPTION_FORMAT = Pattern.compile(
            "^\\s*таблица\\s+\\d+\\s*[\\u2013\\u2014\\-]\\s*\\S.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern NON_DIGIT_AFTER_WORD = Pattern.compile(
            "(?i)таблица\\s+\\D");

    private Ft14TableCaptionChecker() {
    }

    public static List<String> check(List<TableInfo> tables, List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        if (tables == null || tables.isEmpty()) {
            return issues;
        }
        List<ParagraphInfo> paras = paragraphs == null ? List.of() : paragraphs;
        int n = 0;
        for (int i = 0; i < tables.size() && n < MAX_ISSUES; i++) {
            TableInfo t = tables.get(i);
            String cap = t.getCaption();
            int tableFirstParaIdx = t.getParagraphIndex();
            int ord = i + 1;
            String where = whereClause(t, ord, tableFirstParaIdx);

            if (cap == null || cap.isBlank()) {
                add(issues,
                        "" + REQ + " — " + where + ": нет названия над таблицей. Нужно: «Таблица N – Название» "
                                + "(N — номер арабскими цифрами), слева, строкой над таблицей.");
                n++;
                continue;
            }

            String normalized = normalizeCaption(cap);
            if (!CAPTION_FORMAT.matcher(normalized).matches()) {
                add(issues,
                        "" + REQ + " — " + where + ": название не по шаблону «Таблица N – Название таблицы». "
                                + "Найдено: «" + shorten(cap, 120) + "». "
                                + explainFormatMismatch(cap));
                n++;
                continue;
            }

            Integer capIdx = t.getCaptionParagraphIndex();
            if (capIdx == null) {
                capIdx = findParagraphIndexByExactText(paras, cap);
            }
            if (capIdx == null) {
                add(issues,
                        "" + REQ + " — " + where + ": название по формату есть, но абзац с этим текстом "
                                + "не найден в модели документа — выравнивание слева автоматически не проверено "
                                + "(название должно быть отдельным абзацем над таблицей, по левому краю).");
                n++;
                continue;
            }
            if (capIdx < 0 || capIdx >= paras.size()) {
                add(issues,
                        "" + REQ + " — " + where + ": индекс абзаца названия (#"
                                + capIdx + ") вне диапазона — выравнивание не проверено.");
                n++;
                continue;
            }
            if (capIdx >= tableFirstParaIdx) {
                add(issues,
                        "" + REQ + " — " + where + ": название должно быть над таблицей (абзац названия #"
                                + capIdx + " не выше первого абзаца таблицы #" + tableFirstParaIdx + ").");
                n++;
            }
            if (n >= MAX_ISSUES) {
                break;
            }
            String al = paras.get(capIdx).getAlignment();
            if (al != null && !"LEFT".equalsIgnoreCase(al)) {
                add(issues,
                        "" + REQ + " — " + where + ": название таблицы должно быть выровнено по левому краю "
                                + "(абзац #" + capIdx + "; сейчас: " + al + ").");
                n++;
            }
        }
        return issues;
    }

    private static String whereClause(TableInfo t, int tableOrdinal1Based, int tableFirstParagraphIndex) {
        StringBuilder sb = new StringBuilder();
        int pg = t.getPageIndex();
        if (pg > 0) {
            sb.append("стр. ").append(pg).append(" (оценка по разрывам OOXML), ");
        } else {
            sb.append("стр. не определена, ");
        }
        sb.append("таблица ").append(tableOrdinal1Based).append(" по порядку, первый абзац таблицы #")
                .append(tableFirstParagraphIndex);
        return sb.toString();
    }

    private static String explainFormatMismatch(String rawCaption) {
        if (rawCaption == null) {
            return "";
        }
        String base = "Что не так: после «Таблица» должен идти номер арабскими цифрами (1, 2, 3, …), затем тире и название.";
        if (NON_DIGIT_AFTER_WORD.matcher(normalizeCaption(rawCaption)).find()) {
            return base + " Сейчас вместо цифр — буква или другой символ.";
        }
        return base + " Проверьте тире между номером и текстом.";
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
            String tx = paragraphs.get(i).getText();
            if (tx != null && tx.trim().equals(want)) {
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
