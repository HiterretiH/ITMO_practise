package com.example.backend.check;

import com.example.backend.model.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ФТ-8: гарнитура основного текста Times New Roman, размер 14 pt (рекомендуемый) или не менее 12 pt,
 * цвет чёрный (п. 4.2). Проверяются абзацы «основного текста», см. {@link BodyParagraphRules}.
 */
public final class Ft8MainFontChecker {

    private static final double MIN_PT = 12.0;
    private static final double MAX_RECOMMENDED_PT = 14.0;
    private static final double PT_EPS = 0.05;
    private static final int MAX_ISSUES = 100;
    private static final int TEXT_PREVIEW_MAX = 90;

    private Ft8MainFontChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (!BodyParagraphRules.isMainBodyTextForFormatting(p)) {
                continue;
            }
            String loc = formatLocation(p, i);
            String preview = paragraphTextPreview(p);

            if (p.isRunFontViolatesTnr()) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                String found = p.getFt8NonTnrFontsFound();
                String detail = (found != null && !found.isBlank())
                        ? String.format(Locale.ROOT, " Обнаружены гарнитуры: %s.", shorten(found, 120))
                        : "";
                issues.add(String.format(Locale.ROOT,
                        "ФТ-8: %s шрифт (п. 4.2) — для основного текста требуется Times New Roman; в абзаце есть фрагменты с другим начертанием (в т.ч. локально поверх стиля).%s%s",
                        loc, detail, preview));
            } else {
                String fn = p.getFontName();
                if (fn != null && !isTimesNewRoman(fn)) {
                    if (issues.size() >= MAX_ISSUES) {
                        break;
                    }
                    issues.add(String.format(Locale.ROOT,
                            "ФТ-8: %s шрифт (п. 4.2) — ожидается Times New Roman по абзацу; по сводным данным абзаца указано «%s».%s",
                            loc, shorten(fn, 80), preview));
                }
            }

            if (p.isRunFontSizeViolates()) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                String found = p.getFt8NonCompliantSizesFound();
                String detail = (found != null && !found.isBlank())
                        ? String.format(Locale.ROOT, " Встречаются размеры: %s.", shorten(found, 120))
                        : "";
                issues.add(String.format(Locale.ROOT,
                        "ФТ-8: %s кегль (п. 4.2) — допустимо 12–14 pt; в тексте есть фрагменты вне диапазона (в т.ч. прямое оформление).%s%s",
                        loc, detail, preview));
            } else {
                Double fs = p.getFontSizePt();
                if (fs != null) {
                    if (fs + PT_EPS < MIN_PT) {
                        if (issues.size() >= MAX_ISSUES) {
                            break;
                        }
                        issues.add(String.format(Locale.ROOT,
                                "ФТ-8: %s кегль (п. 4.2) — не менее 12 pt; по абзацу получается %.2f pt (слишком мелко).%s",
                                loc, fs, preview));
                    } else if (fs > MAX_RECOMMENDED_PT + PT_EPS) {
                        if (issues.size() >= MAX_ISSUES) {
                            break;
                        }
                        issues.add(String.format(Locale.ROOT,
                                "ФТ-8: %s кегль (п. 4.2) — рекомендуется 14 pt; по абзацу получается %.2f pt (крупнее нормы).%s",
                                loc, fs, preview));
                    }
                }
            }

            if (p.isRunColorViolatesBlack()) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                String found = p.getFt8NonBlackColorsFound();
                String detail = (found != null && !found.isBlank())
                        ? String.format(Locale.ROOT, " Не чёрные: %s.", shorten(found, 120))
                        : "";
                issues.add(String.format(Locale.ROOT,
                        "ФТ-8: %s цвет текста (п. 4.2) — должен быть чёрный (#000000); в прогонах текста задано иное.%s%s",
                        loc, detail, preview));
            } else if (!isBlackColor(p.getColorHex())) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                issues.add(String.format(Locale.ROOT,
                        "ФТ-8: %s цвет текста (п. 4.2) — должен быть чёрный; по абзацу зафиксировано «%s».%s",
                        loc, p.getColorHex() == null ? "не задано явно" : p.getColorHex(), preview));
            }
        }
        return issues;
    }

    /** Короткий фрагмент текста абзаца для отчёта (ФТ-8 / ФТ-9). */
    public static String paragraphTextPreview(ParagraphInfo p) {
        if (p == null || p.getText() == null) {
            return "";
        }
        String one = p.getText().replace('\n', ' ').replace('\r', ' ').trim();
        if (one.isEmpty()) {
            return "";
        }
        if (one.length() > TEXT_PREVIEW_MAX) {
            return String.format(Locale.ROOT, " Начало текста: «%s…»", one.substring(0, TEXT_PREVIEW_MAX));
        }
        return String.format(Locale.ROOT, " Начало текста: «%s»", one);
    }

    public static boolean isTimesNewRoman(String fontName) {
        if (fontName == null) {
            return false;
        }
        String n = fontName.replace('\u00A0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "");
        return "timesnewroman".equals(n);
    }

    public static boolean isBlackColor(String colorHex) {
        if (colorHex == null || colorHex.isBlank()) {
            return true;
        }
        String c = colorHex.replace("#", "").trim().toUpperCase(Locale.ROOT);
        if ("AUTO".equals(c)) {
            return true;
        }
        return "000000".equals(c) || "00000".equals(c);
    }

    private static String formatLocation(ParagraphInfo p, int index) {
        Integer pg = p.getPageIndex();
        if (pg != null) {
            return String.format(Locale.ROOT, "стр. %d, абз. #%d —", pg, index);
        }
        return String.format(Locale.ROOT, "абз. #%d —", index);
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
