package com.example.backend.config.checks;

import com.example.backend.check.Ft10MarginsParams;
import com.example.backend.check.Ft11HeadingParams;
import com.example.backend.check.Ft16OptionalSectionsParams;
import com.example.backend.check.Ft17SectionTitleParams;
import com.example.backend.check.Ft18SectionTitleParams;
import com.example.backend.check.Ft20SectionTitleParams;
import com.example.backend.check.Ft21ListParams;
import com.example.backend.check.Ft4SectionTitlesParams;
import com.example.backend.check.Ft6SectionTitlesParams;
import com.example.backend.check.Ft7TocParams;
import com.example.backend.check.Ft8RuleParams;
import com.example.backend.check.Ft9ParagraphParams;

import java.util.Locale;
import java.util.Optional;

/**
 * Контекст текущего прогона проверок: параметры из {@code checks-config.json} для согласованности
 * (например, ФТ-7 с ФТ-15/16 при распознавании оглавления).
 */
public final class CheckSession {

    private static final ThreadLocal<ChecksConfigRoot> ROOT = new ThreadLocal<>();

    private CheckSession() {
    }

    public static void begin(ChecksConfigRoot config) {
        ROOT.set(config);
    }

    public static void end() {
        ROOT.remove();
    }

    public static Optional<CheckRuleDefinition> rule(String id) {
        ChecksConfigRoot root = ROOT.get();
        if (root == null || root.rules() == null || id == null || id.isBlank()) {
            return Optional.empty();
        }
        String want = id.trim().toLowerCase(Locale.ROOT);
        return root.rules().stream()
                .filter(r -> r.id() != null && want.equals(r.id().trim().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static Ft4SectionTitlesParams ft4() {
        return rule("ft4").map(Ft4SectionTitlesParams::fromRule).orElseGet(Ft4SectionTitlesParams::defaults);
    }

    public static Ft6SectionTitlesParams ft6() {
        return rule("ft6").map(Ft6SectionTitlesParams::fromRule).orElseGet(Ft6SectionTitlesParams::defaults);
    }

    public static Ft7TocParams ft7() {
        return rule("ft7").map(Ft7TocParams::fromRule).orElseGet(Ft7TocParams::defaults);
    }

    public static Ft8RuleParams ft8() {
        return rule("ft8").map(Ft8RuleParams::fromRule).orElseGet(Ft8RuleParams::defaults);
    }

    public static Ft9ParagraphParams ft9() {
        return rule("ft9").map(Ft9ParagraphParams::fromRule).orElseGet(Ft9ParagraphParams::defaults);
    }

    public static Ft10MarginsParams ft10() {
        return rule("ft10").map(Ft10MarginsParams::fromRule).orElseGet(Ft10MarginsParams::defaults);
    }

    public static Ft11HeadingParams ft11() {
        return rule("ft11").map(Ft11HeadingParams::fromRule).orElseGet(Ft11HeadingParams::defaults);
    }

    public static Ft16OptionalSectionsParams ft16() {
        return rule("ft16").map(Ft16OptionalSectionsParams::fromRule).orElseGet(Ft16OptionalSectionsParams::defaults);
    }

    public static Ft17SectionTitleParams ft17() {
        return rule("ft17").map(Ft17SectionTitleParams::fromRule).orElseGet(Ft17SectionTitleParams::defaults);
    }

    public static Ft18SectionTitleParams ft18() {
        return rule("ft18").map(Ft18SectionTitleParams::fromRule).orElseGet(Ft18SectionTitleParams::defaults);
    }

    public static Ft20SectionTitleParams ft20() {
        return rule("ft20").map(Ft20SectionTitleParams::fromRule).orElseGet(Ft20SectionTitleParams::defaults);
    }

    public static Ft21ListParams ft21() {
        return rule("ft21").map(Ft21ListParams::fromRule).orElseGet(Ft21ListParams::defaults);
    }
}
