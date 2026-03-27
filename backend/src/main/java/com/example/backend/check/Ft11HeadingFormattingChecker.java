package com.example.backend.check;

import com.example.backend.config.checks.CheckSession;
import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ФТ-11: оформление заголовков разделов и подразделов (п. 4.4.4, Приложение 1).
 * <ul>
 *   <li>Главы (уровень 1): ровно один из трёх допустимых вариантов на весь документ.</li>
 *   <li>Подразделы: стандартный регистр с прописной буквы, полужирный допускается.</li>
 *   <li>Без точки в конце текста заголовка — для всех уровней заголовков.</li>
 *   <li>Переносы (U+00AD, OOXML softHyphen; не визуальная вёрстка строк), аббревиатуры, отступ — только для заголовков разделов.</li>
 * </ul>
 */
public final class Ft11HeadingFormattingChecker {

    private static final int MAX_ISSUES = 120;

    private static final Pattern SUBSECTION_NUM = Pattern.compile(
            "^\\s*(?<num>\\d+(?:\\.\\d+)+)(?!\\.)\\s+",
            Pattern.UNICODE_CASE
    );
    private static final Pattern CHAPTER_NUM = Pattern.compile(
            "^\\s*(?<n>\\d+)\\.\\s+",
            Pattern.UNICODE_CASE
    );
    /** Латиница «как аббревиатура» — подряд заглавные буквы. */
    private static final Pattern LATIN_ABBREV_LIKE = Pattern.compile("\\b[A-Z]{2,12}\\b");
    private static final Pattern STYLE_HEADING_NUM = Pattern.compile(
            "(?i)(?:heading|заголовок)\\D*(\\d+)");
    private Ft11HeadingFormattingChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return issues;
        }

        List<HeadingCandidate> heads = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (!isFt11Candidate(p)) {
                continue;
            }
            Integer ol = effectiveOutlineLevel(p);
            if (ol == null || ol < 0 || ol > 5) {
                continue;
            }
            String raw = safeTrim(p.getText());
            if (raw.isEmpty()) {
                continue;
            }
            heads.add(new HeadingCandidate(i, p, ol, raw));
        }

        if (heads.isEmpty()) {
            issues.add("ФТ-11: не найдено абзацев-заголовков с уровнем структуры (outline / стиль Heading) — проверка оформления заголовков пропущена.");
            return issues;
        }

        List<HeadingCandidate> chapters = new ArrayList<>();
        for (HeadingCandidate h : heads) {
            if (h.outlineLevel == 0) {
                chapters.add(h);
            }
        }

        if (!chapters.isEmpty()) {
            Map<ChapterVariant, Integer> counts = new LinkedHashMap<>();
            List<HeadingCandidate> invalidChapter = new ArrayList<>();
            for (HeadingCandidate h : chapters) {
                String core = stripNumberPrefix(h.rawText);
                ChapterVariant v = classifyChapterVariant(h.paragraph, core);
                if (v == ChapterVariant.NONE) {
                    invalidChapter.add(h);
                } else {
                    counts.merge(v, 1, Integer::sum);
                }
            }
            long distinctValid = counts.keySet().stream().filter(k -> k != ChapterVariant.NONE).count();
            if (distinctValid > 1) {
                StringBuilder sb = new StringBuilder(
                        "ФТ-11 (п. 4.4.4): заголовки разделов (глав) первого уровня должны быть оформлены единообразно "
                                + "в соответствии с одним из трёх допустимых вариантов: "
                                + "(1) прописные буквы, полужирное начертание; "
                                + "(2) с прописной буквы (стандартный регистр), обычное начертание; "
                                + "(3) с прописной буквы (стандартный регистр), полужирное начертание. "
                                + "В документе встречаются сразу несколько из этих вариантов: ");
                boolean first = true;
                for (Map.Entry<ChapterVariant, Integer> e : counts.entrySet()) {
                    if (e.getKey() == ChapterVariant.NONE) {
                        continue;
                    }
                    if (!first) {
                        sb.append("; ");
                    }
                    first = false;
                    sb.append(e.getKey().getDescription()).append(" — ").append(e.getValue()).append(" шт.");
                }
                sb.append(". Выберите один вариант и примените его ко всем заголовкам глав.");
                addIssue(issues, sb.toString());
            } else if (invalidChapter.size() == chapters.size()) {
                addIssue(issues,
                        "ФТ-11 (п. 4.4.4): заголовки разделов (глав) должны соответствовать одному из трёх вариантов: "
                                + "(1) прописные буквы, полужирное начертание; "
                                + "(2) с прописной буквы (стандартный регистр), обычное начертание; "
                                + "(3) с прописной буквы (стандартный регистр), полужирное начертание. "
                                + "Текущие заголовки не попадают ни в один из них.");
            }

            if (invalidChapter.size() != chapters.size()) {
                for (HeadingCandidate h : invalidChapter) {
                    if (issues.size() >= MAX_ISSUES) {
                        break;
                    }
                    String core = stripNumberPrefix(h.rawText);
                    addIssue(issues, String.format(Locale.ROOT,
                            "ФТ-11: %s, абз. #%d — заголовок главы не попадает ни в один из трёх допустимых вариантов (п. 4.4.4). "
                                    + "Текст (фрагмент): «%s». %s",
                            labelForOutline(0), h.index, shorten(core, 100),
                            describeChapterFormattingForIssue(h.paragraph, core)));
                }
            }
        }

        for (HeadingCandidate h : heads) {
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            String label = labelForOutline(h.outlineLevel);
            String core = stripNumberPrefix(h.rawText);
            String titleForChecks = core.isEmpty() ? h.rawText.trim() : core;

            if (h.outlineLevel >= 1) {
                if (isAllCapsTitle(titleForChecks)) {
                    addIssue(issues, String.format(Locale.ROOT,
                            "ФТ-11: %s, абз. #%d — подразделы должны быть с прописной буквы в стандартном регистре (п. 4.4.4), "
                                    + "а не полностью прописными. Начало: «%s».",
                            label, h.index, shorten(titleForChecks, 120)));
                }
            }

            checkTrailingDot(issues, label, h.index, titleForChecks, h.rawText);
            // п. 4.4.4: отсутствие переносов; запрет аббревиатур; абзацный отступ — по смыслу ТЗ только у заголовков разделов (глав).
            if (h.outlineLevel == 0) {
                checkHyphenation(issues, label, h.index, h.rawText, h.paragraph);
                checkAbbreviations(issues, label, h.index, h.rawText, titleForChecks);
                checkFirstLineIndent(issues, label, h.index, h.paragraph);
            }
        }

        return issues;
    }

    private static void checkTrailingDot(
            List<String> issues, String label, int index, String titleCore, String raw) {
        String t = titleCore.trim();
        if (t.isEmpty()) {
            return;
        }
        char last = t.charAt(t.length() - 1);
        if (last == '.') {
            addIssue(issues, String.format(Locale.ROOT,
                    "ФТ-11: %s, абз. #%d — в конце заголовка не должно быть точки после текста (п. 4.4.4). «%s»",
                    label, index, shorten(raw, 150)));
        }
    }

    private static void checkHyphenation(List<String> issues, String label, int index, String raw, ParagraphInfo p) {
        if (raw == null) {
            return;
        }
        if (p != null && p.isOoxmlDiscretionaryHyphenMarks()) {
            addIssue(issues, String.format(Locale.ROOT,
                    "ФТ-11: %s, абз. #%d — в разметке заголовка есть автоматический перенос Word "
                            + "(w:softHyphen / w:optionalHyphen) (п. 4.4.4). "
                            + "Чисто визуальный перенос по ширине страницы в .docx не записывается и по файлу не проверяется.",
                    label, index));
        }
        if (raw.indexOf('\u00AD') >= 0) {
            addIssue(issues, String.format(Locale.ROOT,
                    "ФТ-11: %s, абз. #%d — в заголовке не допускается мягкий перенос (символ U+00AD) (п. 4.4.4).",
                    label, index));
            return;
        }
        if (raw.contains("-\n") || raw.contains("-\r")) {
            addIssue(issues, String.format(Locale.ROOT,
                    "ФТ-11: %s, абз. #%d — в заголовке обнаружен перенос слова по дефису (п. 4.4.4).",
                    label, index));
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.matches(".*[\\p{L}]-\\s*$")) {
            addIssue(issues, String.format(Locale.ROOT,
                    "ФТ-11: %s, абз. #%d — в конце строки заголовка дефис с пробелом похож на перенос слова (п. 4.4.4). «%s»",
                    label, index, shorten(trimmed, 120)));
        }
    }

    private static void checkAbbreviations(
            List<String> issues, String label, int index, String raw, String titleCore) {
        if (raw == null) {
            return;
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher m = LATIN_ABBREV_LIKE.matcher(raw);
        while (m.find()) {
            found.add(m.group());
        }
        boolean mixedRegister = titleCore != null && titleCore.matches(".*[а-яёa-z].*");
        if (mixedRegister) {
            for (String tok : splitHeadingTokens(titleCore)) {
                String clean = stripTokenPunctuation(tok);
                if (clean.length() >= 2 && clean.length() <= 4 && clean.matches("[А-ЯЁ]+")) {
                    found.add(clean);
                }
            }
        }
        if (found.isEmpty()) {
            return;
        }
        addIssue(issues, String.format(Locale.ROOT,
                "ФТ-11: %s, абз. #%d — в названии заголовка не допускаются аббревиатуры (п. 4.4.4). "
                        + "Подозрение на сокращения (эвристика): %s. Текст: «%s»",
                label, index, String.join(", ", found), shorten(raw, 150)));
    }

    private static List<String> splitHeadingTokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String w : text.replace('\u00A0', ' ').split("\\s+")) {
            if (!w.isBlank()) {
                out.add(w.trim());
            }
        }
        return out;
    }

    private static String stripTokenPunctuation(String tok) {
        String t = tok;
        while (!t.isEmpty() && "«»\"'([{".indexOf(t.charAt(0)) >= 0) {
            t = t.substring(1);
        }
        while (!t.isEmpty() && "»\"'.),;:!?]}".indexOf(t.charAt(t.length() - 1)) >= 0) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static void checkFirstLineIndent(List<String> issues, String label, int index, ParagraphInfo p) {
        Ft11HeadingParams hp = CheckSession.ft11();
        Double fl = p.getFirstLineIndentCm();
        Double li = p.getLeftIndentCm();
        if (fl == null && li == null) {
            return;
        }
        boolean ok = false;
        if (fl != null && Math.abs(fl - hp.firstLineIndentCmExpected()) <= hp.firstLineIndentEps()) {
            ok = true;
        }
        if (!ok && li != null && Math.abs(li - hp.firstLineIndentCmExpected()) <= hp.firstLineIndentEps()) {
            ok = true;
        }
        if (ok) {
            return;
        }
        if (fl != null || li != null) {
            addIssue(issues, String.format(Locale.ROOT,
                    "ФТ-11: %s, абз. #%d — заголовок должен начинаться с абзацного отступа ~%.2f см (п. 4.4.4). "
                            + "В абзаце: отступ первой строки=%s см, левый отступ=%s см.",
                    label, index, hp.firstLineIndentCmExpected(),
                    fl == null ? "не задан" : String.format(Locale.ROOT, "%.2f", fl),
                    li == null ? "не задан" : String.format(Locale.ROOT, "%.2f", li)));
        }
    }

    private static boolean isFt11Candidate(ParagraphInfo p) {
        if (p == null || p.isInTable() || p.isContainsFormula()) {
            return false;
        }
        String t = p.getText();
        if (t == null || t.isBlank()) {
            return false;
        }
        if (BodyParagraphRules.looksLikeTocEntryLine(t)) {
            return false;
        }
        if (isTocStyleStrict(p)) {
            return false;
        }
        String trim = t.trim();
        if (trim.matches("(?is)^\\s*(рисунок|таблица|листинг)\\b.*")) {
            return false;
        }
        return effectiveOutlineLevel(p) != null;
    }

    /** Содержание / оглавление — не тело документа для ФТ-11. */
    private static boolean isTocStyleStrict(ParagraphInfo p) {
        String sid = p.getStyleId();
        if (sid != null) {
            String u = sid.toUpperCase(Locale.ROOT);
            if (u.contains("TOC")) {
                return true;
            }
        }
        String sn = p.getStyleName();
        if (sn != null) {
            String low = sn.toLowerCase(Locale.ROOT);
            if (low.contains("toc") || (low.contains("оглавлен") && !low.contains("заголовок"))) {
                return true;
            }
        }
        Integer ol = p.getOutlineLevel();
        return ol != null && ol == 9;
    }

    /**
     * Эффективный уровень: outline из абзаца или из имени стиля Heading N.
     */
    private static Integer effectiveOutlineLevel(ParagraphInfo p) {
        Integer ol = p.getOutlineLevel();
        if (ol != null && ol != 9) {
            return ol;
        }
        String id = p.getStyleId();
        if (id != null) {
            Matcher m = STYLE_HEADING_NUM.matcher(id);
            if (m.find()) {
                int n = Integer.parseInt(m.group(1));
                return Math.max(0, n - 1);
            }
        }
        String sn = p.getStyleName();
        if (sn != null) {
            Matcher m = STYLE_HEADING_NUM.matcher(sn);
            if (m.find()) {
                int n = Integer.parseInt(m.group(1));
                return Math.max(0, n - 1);
            }
        }
        if (ol != null && ol == 9) {
            return null;
        }
        return ol;
    }

    private static String labelForOutline(int outlineLevel) {
        if (outlineLevel <= 0) {
            return "заголовок раздела (глава, уровень структуры 1)";
        }
        return String.format(Locale.ROOT,
                "заголовок подраздела (уровень структуры %d)", outlineLevel + 1);
    }

    private static String stripNumberPrefix(String line) {
        if (line == null) {
            return "";
        }
        String t = line.replace('\u00A0', ' ');
        Matcher mSub = SUBSECTION_NUM.matcher(t);
        if (mSub.find()) {
            return safeTrim(t.substring(mSub.end()));
        }
        Matcher mCh = CHAPTER_NUM.matcher(t);
        if (mCh.find()) {
            return safeTrim(t.substring(mCh.end()));
        }
        return safeTrim(t);
    }

    private static boolean isAllCapsTitle(String core) {
        if (core == null || core.isEmpty()) {
            return false;
        }
        String letters = core.replaceAll("[^\\p{L}]", "");
        if (letters.isEmpty()) {
            return false;
        }
        return letters.equals(letters.toUpperCase(Locale.ROOT));
    }

    /**
     * Стандартный регистр: не «все прописные» и есть хотя бы одна строчная буква (кириллица/латиница).
     */
    private static boolean isStandardRegisterTitle(String core) {
        if (core == null || core.isEmpty()) {
            return false;
        }
        if (isAllCapsTitle(core)) {
            return false;
        }
        return core.matches(".*[а-яёa-z].*");
    }

    private static ChapterVariant classifyChapterVariant(ParagraphInfo p, String titleCore) {
        boolean poluzhirnoye = hasPoluzhirnoyeNachertanie(p);
        boolean capsFont = Boolean.TRUE.equals(p.getCaps());
        boolean allCapsText = isAllCapsTitle(titleCore) || capsFont;

        boolean standardReg = isStandardRegisterTitle(titleCore);

        if (allCapsText && poluzhirnoye) {
            return ChapterVariant.V1_ALLCAPS_BOLD;
        }
        if (standardReg && !poluzhirnoye) {
            return ChapterVariant.V2_TITLE_NORMAL;
        }
        if (standardReg && poluzhirnoye) {
            return ChapterVariant.V3_TITLE_BOLD;
        }
        return ChapterVariant.NONE;
    }

    /**
     * Полужирное начертание по п. 4.4.4: в Word это часто «полужирный» шрифт (Semibold) или флаг жирного.
     */
    private static boolean hasPoluzhirnoyeNachertanie(ParagraphInfo p) {
        return Boolean.TRUE.equals(p.getBold()) || Boolean.TRUE.equals(p.getSemiboldEmphasis());
    }

    private static String describeChapterFormattingForIssue(ParagraphInfo p, String titleCore) {
        boolean textAllCaps = isAllCapsTitle(titleCore);
        String reg = textAllCaps
                ? "по тексту заголовка — все буквы прописные"
                : (isStandardRegisterTitle(titleCore)
                        ? "по тексту — стандартный регистр с прописной буквы"
                        : "по тексту — не прописные и не стандартный регистр с прописной буквы");
        boolean zh = Boolean.TRUE.equals(p.getBold());
        boolean sm = Boolean.TRUE.equals(p.getSemiboldEmphasis());
        String nach;
        if (zh && sm) {
            nach = "полужирное: жирный в оформлении и гарнитура Semibold/Demi";
        } else if (zh) {
            nach = "полужирное: жирный в оформлении Word";
        } else if (sm) {
            nach = "полужирное: по гарнитуре (Semibold/Demi), без жирного в оформлении";
        } else {
            nach = "обычное начертание (нет жирного и нет Semibold/Demi в имени шрифта)";
        }
        return reg + "; " + nach + ".";
    }

    private static void addIssue(List<String> issues, String line) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(line);
        }
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.replace('\u00A0', ' ').trim();
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    private record HeadingCandidate(int index, ParagraphInfo paragraph, int outlineLevel, String rawText) {}

    private enum ChapterVariant {
        NONE("—"),
        V1_ALLCAPS_BOLD("вариант 1: прописные буквы, полужирное начертание"),
        V2_TITLE_NORMAL("вариант 2: с прописной буквы (стандартный регистр), обычное начертание"),
        V3_TITLE_BOLD("вариант 3: с прописной буквы (стандартный регистр), полужирное начертание");

        private final String description;

        ChapterVariant(String description) {
            this.description = description;
        }

        String getDescription() {
            return description;
        }
    }
}
