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

    private Ft8MainFontChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (!BodyParagraphRules.isMainBodyTextForFormatting(p)) {
                continue;
            }
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            String loc = formatLocation(p, i);
            String fn = p.getFontName();
            if (fn != null && !isTimesNewRoman(fn)) {
                issues.add(String.format(Locale.ROOT,
                        "ФТ-8: %s ожидается шрифт Times New Roman, фактически «%s».",
                        loc, shorten(fn, 60)));
            }
            Double fs = p.getFontSizePt();
            if (fs != null) {
                if (fs + PT_EPS < MIN_PT) {
                    issues.add(String.format(Locale.ROOT,
                            "ФТ-8: %s размер шрифта не менее 12 pt (п. 4.2), фактически %.2f pt.",
                            loc, fs));
                } else if (fs > MAX_RECOMMENDED_PT + PT_EPS) {
                    issues.add(String.format(Locale.ROOT,
                            "ФТ-8: %s рекомендуется 14 pt (п. 4.2), фактически %.2f pt.",
                            loc, fs));
                }
            }
            if (!isBlackColor(p.getColorHex())) {
                issues.add(String.format(Locale.ROOT,
                        "ФТ-8: %s цвет текста должен быть чёрным, фактически «%s».",
                        loc, p.getColorHex() == null ? "не задан" : p.getColorHex()));
            }
        }
        return issues;
    }

    static boolean isTimesNewRoman(String fontName) {
        String n = fontName.replace('\u00A0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "");
        return "timesnewroman".equals(n);
    }

    static boolean isBlackColor(String colorHex) {
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
            return String.format(Locale.ROOT, "стр. %d, абз. #%d:", pg, index);
        }
        return String.format(Locale.ROOT, "абз. #%d:", index);
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
