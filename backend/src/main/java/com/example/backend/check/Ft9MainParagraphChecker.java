package com.example.backend.check;

import com.example.backend.model.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ФТ-9: межстрочный интервал 1,5; абзацный отступ 1,25 см; выравнивание по ширине (п. 4.2).
 * Только абзацы основного текста, см. {@link BodyParagraphRules}. Если параметр в документе не задан
 * явно (null), проверка по нему пропускается, чтобы не плодить ложные срабатывания.
 */
public final class Ft9MainParagraphChecker {

    private static final double LINE_EXPECTED = 1.5;
    private static final double LINE_EPS = 0.06;
    private static final double INDENT_CM_EXPECTED = 1.25;
    private static final double INDENT_CM_EPS = 0.2;
    private static final int MAX_ISSUES = 100;

    private Ft9MainParagraphChecker() {
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

            Double line = p.getLineSpacing();
            if (line != null && Math.abs(line - LINE_EXPECTED) > LINE_EPS) {
                issues.add(String.format(Locale.ROOT,
                        "ФТ-9: %s межстрочный интервал должен быть 1,5 (п. 4.2), фактически %.2f.",
                        loc, line));
            }

            Double fi = p.getFirstLineIndentCm();
            if (fi != null && Math.abs(fi - INDENT_CM_EXPECTED) > INDENT_CM_EPS) {
                issues.add(String.format(Locale.ROOT,
                        "ФТ-9: %s абзацный отступ должен быть 1,25 см (п. 4.2), фактически %.2f см.",
                        loc, fi));
            }

            String al = p.getAlignment();
            if (al != null && !isJustified(al)) {
                issues.add(String.format(Locale.ROOT,
                        "ФТ-9: %s выравнивание основного текста — по ширине (п. 4.2), фактически «%s».",
                        loc, al));
            }
        }
        return issues;
    }

    /** В Word для «по ширине» в OOXML часто {@code BOTH}. */
    static boolean isJustified(String alignment) {
        if (alignment == null) {
            return false;
        }
        String u = alignment.trim().toUpperCase(Locale.ROOT);
        return "BOTH".equals(u) || "JUSTIFY".equals(u);
    }

    private static String formatLocation(ParagraphInfo p, int index) {
        Integer pg = p.getPageIndex();
        if (pg != null) {
            return String.format(Locale.ROOT, "стр. %d, абз. #%d:", pg, index);
        }
        return String.format(Locale.ROOT, "абз. #%d:", index);
    }
}
