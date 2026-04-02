package com.example.backend.check;

import com.example.backend.config.checks.CheckSession;
import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ФТ-9: междустрочный интервал 1,5; абзацный отступ 1,25 см; выравнивание по ширине (п. 4.2).
 * Только абзацы основного текста, см. {@link BodyParagraphRules}. Если параметр в документе не задан
 * явно (null), проверка по нему пропускается, чтобы не плодить ложные срабатывания.
 */
public final class Ft9MainParagraphChecker {

    private static final int MAX_ISSUES = 100;

    private Ft9MainParagraphChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        Ft9ParagraphParams rp = CheckSession.ft9();
        List<String> issues = new ArrayList<>();
        int[] termsBody = Ft18TermsDefinitionsChecker.termsDefinitionsBodyInclusiveIndexBounds(paragraphs);
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (!BodyParagraphRules.isMainBodyTextForFormatting(p)) {
                continue;
            }
            if (termsBody != null && i >= termsBody[0] && i <= termsBody[1]) {
                continue;
            }
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            String loc = formatLocation(p, i);
            String preview = Ft8MainFontChecker.paragraphTextPreview(p);

            Double line = p.getLineSpacing();
            if (line != null && Math.abs(line - rp.lineSpacing()) > rp.lineSpacingEps()) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                issues.add(String.format(Locale.ROOT,
                        "ФТ-9: %s межстрочный интервал (п. 4.2) — ожидается %s; фактически %.2f (%s).%s",
                        loc, formatHalf(rp.lineSpacing()), line, describeSpacingVersusNorm(line, rp), preview));
            }

            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            Double fi = p.getFirstLineIndentCm();
            if (fi != null && Math.abs(fi - rp.firstLineIndentCm()) > rp.firstLineIndentEps()) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                double delta = fi - rp.firstLineIndentCm();
                issues.add(String.format(Locale.ROOT,
                        "ФТ-9: %s красная строка / абзацный отступ (п. 4.2) — ожидается %.2f см; фактически %.2f см (отклонение %+.2f см).%s",
                        loc, rp.firstLineIndentCm(), fi, delta, preview));
            }

            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            String al = p.getAlignment();
            if (al != null && !isJustified(al)) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                issues.add(String.format(Locale.ROOT,
                        "ФТ-9: %s выравнивание по горизонтали (п. 4.2) — ожидается по ширине; фактически %s (код OOXML: «%s»).%s",
                        loc, alignmentHumanRu(al), al, preview));
            }
        }
        return issues;
    }

    private static String formatHalf(double lineSpacing) {
        if (Math.abs(lineSpacing - 1.5) < 0.01) {
            return "1,5";
        }
        return String.format(Locale.ROOT, "%.2f", lineSpacing).replace('.', ',');
    }

    private static String describeSpacingVersusNorm(double line, Ft9ParagraphParams rp) {
        if (line < rp.lineSpacing() - rp.lineSpacingEps()) {
            return "интервал меньше требуемого " + formatHalf(rp.lineSpacing());
        }
        if (line > rp.lineSpacing() + rp.lineSpacingEps()) {
            return "интервал больше требуемого " + formatHalf(rp.lineSpacing());
        }
        return "отличается от " + formatHalf(rp.lineSpacing());
    }

    private static String alignmentHumanRu(String alignment) {
        if (alignment == null) {
            return "не задано";
        }
        String u = alignment.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "LEFT" -> "по левому краю";
            case "RIGHT" -> "по правому краю";
            case "CENTER" -> "по центру";
            case "BOTH", "JUSTIFY" -> "по ширине";
            case "DISTRIBUTE" -> "по ширине с распределением";
            default -> "иное («" + alignment + "»)";
        };
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
            return String.format(Locale.ROOT, "стр. %d, абз. #%d —", pg, index);
        }
        return String.format(Locale.ROOT, "абз. #%d —", index);
    }
}
