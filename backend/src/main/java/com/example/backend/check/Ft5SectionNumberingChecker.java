package com.example.backend.check;

import com.example.backend.model.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ФТ-5: корректность нумерации глав и подразделов (п. 4.4.3); номер не заканчивается лишней точкой.
 * <p>
 * В диапазоне от «ВВЕДЕНИЕ» до «ЗАКЛЮЧЕНИЕ» (тело основной части) проверяются абзацы-заголовки с
 * {@code outlineLevel} 0–2 и числовым префиксом. Подразделы должны относиться к текущей главе {@code N};
 * подряд идущие нумерованные заголовки одной главы должны образовывать допустимую иерархию: после {@code 2.1}
 * идёт {@code 2.1.1}, затем {@code 2.1.2} или {@code 2.2}; недопустимы «откаты» вроде {@code 2.2}→{@code 2.1.1},
 * пропуск родителя вроде {@code 2.2}→{@code 2.3.1} без заголовка {@code 2.3}, а также прежняя плоская
 * нумерация {@code N.M}→{@code N.(M+1)} для двухсегментных номеров.
 * Префикс первой главы у подразделов не должен
 * расходиться с активной главой (например «2.52» внутри главы 3).
 */
public final class Ft5SectionNumberingChecker {

    /** Подраздел: «3.1 Текст» — минимум два сегмента без точки в конце номера. */
    private static final Pattern SUBSECTION_NUM = Pattern.compile(
            "^\\s*(?<num>\\d+(?:\\.\\d+)+)(?!\\.)\\s+",
            Pattern.UNICODE_CASE
    );

    /** Глава: «3. ТЕКСТ» — один номер с точкой, без второго сегмента. */
    private static final Pattern CHAPTER_NUM = Pattern.compile(
            "^\\s*(?<n>\\d+)\\.\\s+",
            Pattern.UNICODE_CASE
    );

    /** Лишняя точка в конце номера: «1.1. Текст». */
    private static final Pattern TRAILING_DOT_AFTER_NUMBER = Pattern.compile(
            "^\\s*\\d+(?:\\.\\d+)+\\.\\s+",
            Pattern.UNICODE_CASE
    );

    private Ft5SectionNumberingChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        int intro = findOutlineHeadingIndex(paragraphs, "ВВЕДЕНИЕ");
        int concl = findOutlineHeadingIndex(paragraphs, "ЗАКЛЮЧЕНИЕ");
        if (intro < 0 || concl < 0 || concl <= intro) {
            issues.add("ФТ-5: не удалось выделить основную часть (нужны заголовки «ВВЕДЕНИЕ» и «ЗАКЛЮЧЕНИЕ» с уровнем 0).");
            return issues;
        }

        List<NumberedHeading> heads = new ArrayList<>();
        for (int i = intro + 1; i < concl; i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (!isNumberedHeadingCandidate(p)) {
                continue;
            }
            String raw = safeTrim(p.getText());
            if (raw.isEmpty()) {
                continue;
            }
            if (TRAILING_DOT_AFTER_NUMBER.matcher(raw).find()) {
                issues.add("ФТ-5: номер раздела не должен заканчиваться точкой перед текстом (п. 4.4.3): «"
                        + shorten(raw, 90) + "».");
                continue;
            }
            int[] segs = parseNumberPrefix(raw);
            if (segs == null || segs.length == 0) {
                continue;
            }
            heads.add(new NumberedHeading(i, raw, segs));
        }

        int currentChapter = -1;
        for (NumberedHeading h : heads) {
            int[] s = h.segments();
            if (s.length == 1) {
                currentChapter = s[0];
                continue;
            }
            if (currentChapter >= 0 && s[0] != currentChapter) {
                issues.add(String.format(Locale.ROOT,
                        "ФТ-5: подраздел «%s» начинается с %d.%s, активная глава — %d (нумерация должна относиться к текущей главе).",
                        shorten(h.raw(), 70),
                        s[0],
                        tailLabel(s),
                        currentChapter));
            }
        }

        for (int i = 0; i < heads.size() - 1; i++) {
            NumberedHeading ah = heads.get(i);
            NumberedHeading bh = heads.get(i + 1);
            int[] x = ah.segments();
            int[] y = bh.segments();
            if (x[0] != y[0]) {
                continue;
            }
            if (y.length == 1 && x.length >= 2) {
                issues.add(String.format(Locale.ROOT,
                        "ФТ-5: после «%s» не должен сразу идти заголовок главы «%s» без смены номера главы (нарушена иерархия нумерации).",
                        shorten(ah.raw(), 60),
                        shorten(bh.raw(), 60)));
                continue;
            }
            if (x.length == 1 && y.length >= 2) {
                if (!isValidOutlineSuccessor(x, y)) {
                    issues.add(String.format(Locale.ROOT,
                            "ФТ-5: после главы «%s» первый подраздел должен быть «%d.1 …», обнаружено «%s».",
                            shorten(ah.raw(), 60),
                            x[0],
                            shorten(bh.raw(), 60)));
                }
                continue;
            }
            if (x.length >= 2 && y.length >= 2) {
                if (!isValidOutlineSuccessor(x, y)) {
                    issues.add(String.format(Locale.ROOT,
                            "ФТ-5: после «%s» ожидается следующий корректный номер в иерархии раздела, обнаружено «%s» (проверьте порядок 2.1 → 2.1.1 → 2.1.2 / 2.2 и отсутствие «2.2» → «2.1.1» или «2.2» → «2.3.1» без «2.3»).",
                            shorten(ah.raw(), 60),
                            shorten(bh.raw(), 60)));
                }
            }
        }

        return issues;
    }

    /**
     * Допустимый следующий номер в обходе оглавления: первый потомок (…1), следующий брат (+1 к последнему),
     * выход на уровень вверх и следующий брат у предка.
     */
    static boolean isValidOutlineSuccessor(int[] a, int[] b) {
        if (a.length == 0 || b.length == 0) {
            return false;
        }
        if (a[0] != b[0]) {
            return false;
        }
        if (a.length == 1) {
            return b.length == 2 && b[1] == 1;
        }
        if (b.length == a.length + 1) {
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }
            return b[b.length - 1] == 1;
        }
        if (b.length == a.length) {
            for (int i = 0; i < a.length - 1; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }
            return b[b.length - 1] == a[a.length - 1] + 1;
        }
        if (b.length < a.length) {
            int[] tmp = Arrays.copyOf(a, a.length);
            while (tmp.length > b.length) {
                tmp = Arrays.copyOf(tmp, tmp.length - 1);
            }
            if (tmp.length != b.length) {
                return false;
            }
            for (int i = 0; i < tmp.length - 1; i++) {
                if (tmp[i] != b[i]) {
                    return false;
                }
            }
            return b[b.length - 1] == tmp[b.length - 1] + 1;
        }
        return false;
    }

    private static String tailLabel(int[] s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < s.length; i++) {
            if (i > 1) {
                sb.append('.');
            }
            sb.append(s[i]);
        }
        return sb.toString();
    }

    private static int findOutlineHeadingIndex(List<ParagraphInfo> paragraphs, String titleUpper) {
        for (int i = 0; i < paragraphs.size(); i++) {
            ParagraphInfo p = paragraphs.get(i);
            if (p.getOutlineLevel() == null || p.getOutlineLevel() != 0) {
                continue;
            }
            String nt = normalizeTitle(p.getText());
            if (nt.equals(titleUpper)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isNumberedHeadingCandidate(ParagraphInfo p) {
        Integer ol = p.getOutlineLevel();
        if (ol == null || ol < 0 || ol > 2) {
            return false;
        }
        return true;
    }

    /**
     * Сначала более длинный префикс «1.1», затем «1.» как глава.
     */
    private static int[] parseNumberPrefix(String line) {
        String t = line.replace('\u00A0', ' ');
        Matcher mSub = SUBSECTION_NUM.matcher(t);
        if (mSub.find()) {
            String num = mSub.group("num");
            return splitSegments(num);
        }
        Matcher mCh = CHAPTER_NUM.matcher(t);
        if (mCh.find()) {
            return new int[]{Integer.parseInt(mCh.group("n"))};
        }
        return null;
    }

    private static int[] splitSegments(String num) {
        String[] p = num.split("\\.");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            out[i] = Integer.parseInt(p[i]);
        }
        return out;
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

    private static String safeTrim(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').trim();
    }

    private static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String s = text.replace('\n', ' ').trim();
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private record NumberedHeading(int paragraphIndex, String raw, int[] segments) {}
}
