package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ФТ-19: оформление формул и уравнений (п. 4.7).
 * <p>
 * <b>Что считается формулой:</b> абзац, в XML которого есть OMML — элементы {@code m:oMath} / {@code oMathPara}
 * (в Word — «Вставка → Уравнение», в LibreOffice — «Вставка → Объект → Формула» / встроенный редактор формул).
 * Это же выставляет {@link com.example.backend.domain.ParagraphInfo#isContainsFormula()} в {@code DocxLoadService}.
 * Обычный текст, номера разделов («1 Введение»), строки оглавления — <em>не</em> формулы и не проверяются как таковые.
 * <p>
 * Ссылки на пронумерованные формулы, «где» перед пояснениями, порядок расшифровки символов,
 * единообразие единиц (русские / международные), неразрывный пробел только перед <em>известными</em> обозначениями единиц.
 */
public final class Ft19FormulasChecker {

    private static final String REQ = "п. 4.7 — формулы и уравнения";

    private static final int MAX_ISSUES = 60;

    /** Номер в конце строки формулы: табуляция и (N) или (N.M). */
    private static final Pattern FORMULA_NUMBER_TAIL =
            Pattern.compile("(?:[\\t\\u00A0\\s]+)\\(\\s*(\\d+(?:\\.\\d+)?)\\s*\\)\\s*$");

    private static final Pattern FORMULA_NUMBER_TAIL_NO_TAB =
            Pattern.compile("\\(\\s*(\\d+(?:\\.\\d+)?)\\s*\\)\\s*$");

    /** Русский ↔ международный (SI) — расширенный список пар для проверки единообразия. */
    private static final List<Map.Entry<String, String>> UNIT_RU_SI_PAIRS = List.of(
            Map.entry("мм", "mm"),
            Map.entry("см", "cm"),
            Map.entry("м", "m"),
            Map.entry("км", "km"),
            Map.entry("мм²", "mm²"),
            Map.entry("см²", "cm²"),
            Map.entry("м²", "m²"),
            Map.entry("км²", "km²"),
            Map.entry("мм³", "mm³"),
            Map.entry("см³", "cm³"),
            Map.entry("м³", "m³"),
            Map.entry("мг", "mg"),
            Map.entry("г", "g"),
            Map.entry("кг", "kg"),
            Map.entry("т", "t"),
            Map.entry("с", "s"),
            Map.entry("мин", "min"),
            Map.entry("ч", "h"),
            Map.entry("сут", "d"),
            Map.entry("мс", "ms"),
            Map.entry("мкс", "µs"),
            Map.entry("нс", "ns"),
            Map.entry("мА", "mA"),
            Map.entry("А", "A"),
            Map.entry("кА", "kA"),
            Map.entry("мВ", "mV"),
            Map.entry("В", "V"),
            Map.entry("кВ", "kV"),
            Map.entry("МВ", "MV"),
            Map.entry("Вт", "W"),
            Map.entry("кВт", "kW"),
            Map.entry("МВт", "MW"),
            Map.entry("Гц", "Hz"),
            Map.entry("рад", "rad"),
            Map.entry("Па", "Pa"),
            Map.entry("кПа", "kPa"),
            Map.entry("МПа", "MPa"),
            Map.entry("гПа", "GPa"),
            Map.entry("Н", "N"),
            Map.entry("кН", "kN"),
            Map.entry("Дж", "J"),
            Map.entry("Кл", "C"),
            Map.entry("К", "K"),
            Map.entry("мл", "mL"),
            Map.entry("л", "L"),
            Map.entry("моль", "mol"),
            Map.entry("моль/л", "mol/L"),
            Map.entry("Ом", "Ω"),
            Map.entry("мОм", "mΩ"),
            Map.entry("кОм", "kΩ"),
            Map.entry("МОм", "MΩ"),
            Map.entry("Ф", "F"),
            Map.entry("мФ", "mF"),
            Map.entry("мкФ", "µF"),
            Map.entry("Гн", "H"),
            Map.entry("мГн", "mH"),
            Map.entry("Тл", "T"),
            Map.entry("мТл", "mT"),
            Map.entry("Вб", "Wb"),
            Map.entry("м/с", "m/s"),
            Map.entry("км/ч", "km/h"),
            Map.entry("м/с²", "m/s²"),
            Map.entry("кг/м³", "kg/m³"),
            Map.entry("кг/м²", "kg/m²"),
            Map.entry("Вт/м²", "W/m²"),
            Map.entry("В/м", "V/m"),
            Map.entry("А/м", "A/m"),
            Map.entry("бит/с", "bit/s"),
            Map.entry("байт", "B"),
            Map.entry("кбайт", "KB"),
            Map.entry("Мбайт", "MB"),
            Map.entry("Гбайт", "GB"));

    /**
     * Все известные обозначения единиц (рус + SI), без дубликатов; для паттерна — длинные первыми,
     * чтобы «мм» не резалось как «м».
     */
    private static final List<String> ALL_KNOWN_UNIT_TOKENS = buildAllKnownUnitTokens();

    /** {@code число + пробел(ы) + известная единица} — только из белого списка (не цепляет «1 Контекст»). */
    private static final Pattern NUMBER_THEN_KNOWN_UNIT = buildNumberThenKnownUnitPattern();

    private static List<String> buildAllKnownUnitTokens() {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : UNIT_RU_SI_PAIRS) {
            s.add(e.getKey());
            s.add(e.getValue());
        }
        return s.stream().sorted(Comparator.comparingInt(String::length).reversed()).collect(Collectors.toList());
    }

    private static Pattern buildNumberThenKnownUnitPattern() {
        String alt =
                ALL_KNOWN_UNIT_TOKENS.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile("(\\d+)([ \\u00A0]+)(" + alt + ")(?![а-яА-ЯёЁa-zA-Z0-9²³°])");
    }

    private Ft19FormulasChecker() {
    }

    public static List<String> check(List<ParagraphInfo> paragraphs, String fullText) {
        List<String> issues = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return issues;
        }

        String scanText = fullText != null && !fullText.isBlank() ? fullText : joinAllParagraphTexts(paragraphs);
        issues.addAll(checkUnitMixingAndNbsp(scanText, paragraphs));

        List<Integer> formulaIndices = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (paragraphs.get(i).isContainsFormula()) {
                formulaIndices.add(i);
            }
        }
        if (formulaIndices.isEmpty()) {
            return issues;
        }

        String textForRefs = buildTextExcludingFormulaParagraphs(paragraphs);

        for (int fi : formulaIndices) {
            ParagraphInfo fp = paragraphs.get(fi);
            String raw = fp.getText();
            if (raw == null) {
                raw = "";
            }
            String num = extractFormulaNumber(raw);
            if (num != null) {
                if (!referenceExistsForNumber(textForRefs, num)) {
                    issues.add(
                            String.format(
                                    Locale.ROOT,
                                    "ФТ-19: %s — для формулы с номером (%s) в тексте не найдена ссылка «(%s)» "
                                            + "(кроме строки самой формулы). Как исправить: добавьте в текст ссылку на формулу в круглых скобках.",
                                    REQ,
                                    num,
                                    num));
                }
            }

            issues.addAll(checkBlankLinesAroundFormula(paragraphs, fi));
            issues.addAll(checkGdeAndExplanations(paragraphs, fi, raw));

            if (issues.size() >= MAX_ISSUES) {
                return issues;
            }
        }

        for (int fi : formulaIndices) {
            issues.addAll(checkSymbolOrder(paragraphs, fi));
            if (issues.size() >= MAX_ISSUES) {
                return issues;
            }
        }

        return issues;
    }

    private static String buildTextExcludingFormulaParagraphs(List<ParagraphInfo> paragraphs) {
        StringBuilder sb = new StringBuilder();
        for (ParagraphInfo p : paragraphs) {
            if (!p.isContainsFormula()) {
                String t = p.getText();
                if (t != null) {
                    sb.append(t).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static String joinAllParagraphTexts(List<ParagraphInfo> paragraphs) {
        StringBuilder sb = new StringBuilder();
        for (ParagraphInfo p : paragraphs) {
            if (p.getText() != null) {
                sb.append(p.getText()).append('\n');
            }
        }
        return sb.toString();
    }

    static String extractFormulaNumber(String paragraphText) {
        if (paragraphText == null) {
            return null;
        }
        String t = paragraphText.replace('\u00A0', ' ').trim();
        Matcher m = FORMULA_NUMBER_TAIL.matcher(t);
        if (m.find()) {
            return m.group(1);
        }
        Matcher m2 = FORMULA_NUMBER_TAIL_NO_TAB.matcher(t);
        if (m2.find()) {
            return m2.group(1);
        }
        return null;
    }

    private static boolean referenceExistsForNumber(String textWithoutFormulaLines, String num) {
        Pattern p = Pattern.compile("\\(\\s*" + Pattern.quote(num) + "\\s*\\)");
        return p.matcher(textWithoutFormulaLines).find();
    }

    private static List<String> checkBlankLinesAroundFormula(List<ParagraphInfo> paragraphs, int i) {
        List<String> issues = new ArrayList<>();
        if (i > 0) {
            ParagraphInfo prev = paragraphs.get(i - 1);
            if (!isBlankParagraph(prev)) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "ФТ-19: %s — перед формулой (абзац %d) должна быть пустая строка (отдельное выделение формулы). "
                                        + "Как исправить: вставьте пустой абзац перед строкой с формулой.",
                                REQ,
                                i));
            }
        }
        if (i + 1 < paragraphs.size()) {
            ParagraphInfo next = paragraphs.get(i + 1);
            String nt = safeTrim(next.getText());
            if (!isBlankParagraph(next) && !startsWithGde(nt) && !isGdeContinuationOk(paragraphs, i)) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "ФТ-19: %s — после формулы (абзац %d) ожидается пустая строка или абзац с пояснениями, начинающийся со слова «где». "
                                        + "Как исправить: вставьте пустой абзац или начните пояснения с «где …».",
                                REQ,
                                i));
            }
        }
        return issues;
    }

    /** Если в той же строке формулы уже есть «, где». */
    private static boolean isGdeContinuationOk(List<ParagraphInfo> paragraphs, int i) {
        String t = paragraphs.get(i).getText();
        if (t == null) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return lower.contains(", где") || lower.contains(",где");
    }

    private static boolean isBlankParagraph(ParagraphInfo p) {
        return p.getText() == null || p.getText().trim().isEmpty();
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.replace('\u00A0', ' ').trim();
    }

    private static boolean startsWithGde(String line) {
        String t = safeTrim(line).toLowerCase(Locale.ROOT);
        return t.startsWith("где ") || t.startsWith("где,") || t.equals("где");
    }

    private static List<String> checkGdeAndExplanations(List<ParagraphInfo> paragraphs, int i, String formulaRaw) {
        List<String> issues = new ArrayList<>();
        if (isGdeContinuationOk(paragraphs, i)) {
            return issues;
        }
        int j = i + 1;
        while (j < paragraphs.size() && isBlankParagraph(paragraphs.get(j))) {
            j++;
        }
        if (j >= paragraphs.size()) {
            return issues;
        }
        String nextText = safeTrim(paragraphs.get(j).getText());
        if (startsWithGde(nextText)) {
            return issues;
        }
        if (looksLikeSymbolExplanationLine(nextText) && !startsWithGde(nextText)) {
            issues.add(
                    String.format(
                            Locale.ROOT,
                            "ФТ-19: %s — пояснения к символам после формулы (абзац %d) должны начинаться со слова «где» "
                                    + "(часто после запятой в конце строки формулы или с новой строки). "
                                    + "Как исправить: начните блок пояснений с «где …».",
                            REQ,
                            j));
        }
        return issues;
    }

    /** Строка вида «a — пояснение» или «α — …» без ведущего «где». */
    private static boolean looksLikeSymbolExplanationLine(String line) {
        if (line.length() < 3) {
            return false;
        }
        return line.matches(
                "(?i)^[\\s]*[a-zA-Zα-ωΑ-Ωа-яА-ЯёЁ0-9]{1,6}\\s*[—–\\-]\\s*.+");
    }

    /**
     * Порядок расшифровок в блоке «где» сравнивается с порядком первых вхождений латинских букв
     * в тексте формулы (после удаления номера в скобках).
     */
    private static List<String> checkSymbolOrder(List<ParagraphInfo> paragraphs, int formulaIndex) {
        List<String> issues = new ArrayList<>();
        String raw = paragraphs.get(formulaIndex).getText();
        if (raw == null) {
            return issues;
        }
        String formulaBody = stripTrailingNumber(raw).trim();
        List<String> lettersInFormula = firstOccurrenceLatinLetters(formulaBody);
        if (lettersInFormula.isEmpty()) {
            return issues;
        }

        List<String> explained = new ArrayList<>();
        for (int j = formulaIndex + 1; j < paragraphs.size(); j++) {
            ParagraphInfo p = paragraphs.get(j);
            if (p.isContainsFormula()) {
                break;
            }
            String t = safeTrim(p.getText());
            if (t.isEmpty()) {
                continue;
            }
            if (isOutlineLevel0Heading(p)) {
                break;
            }
            if (startsWithGde(t)) {
                t = t.replaceFirst("(?i)^где[,\\s]*", "").trim();
            }
            for (String part : splitWhereExplanations(t)) {
                String sym = extractLeadingSymbol(part);
                if (sym != null && sym.length() == 1 && Character.isLetter(sym.charAt(0))) {
                    explained.add(sym.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (explained.isEmpty()) {
            return issues;
        }
        int n = Math.min(lettersInFormula.size(), explained.size());
        for (int k = 0; k < n; k++) {
            if (!lettersInFormula.get(k).equalsIgnoreCase(explained.get(k))) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "ФТ-19: %s — порядок расшифровки символов после «где» не совпадает с порядком вхождений в формуле: "
                                        + "ожидалось «%s», в пояснении — «%s» (позиция %d). "
                                        + "Как исправить: переставьте пояснения в том же порядке, что и символы в формуле.",
                                REQ,
                                lettersInFormula.get(k),
                                explained.get(k),
                                k + 1));
                break;
            }
        }
        return issues;
    }

    private static boolean isOutlineLevel0Heading(ParagraphInfo p) {
        Integer ol = p.getOutlineLevel();
        return ol != null && ol == 0 && !safeTrim(p.getText()).isEmpty();
    }

    private static String stripTrailingNumber(String paragraphText) {
        String t = paragraphText.replace('\u00A0', ' ').trim();
        t = t.replaceAll("(?:[\\t\\s]+)\\(\\s*\\d+(?:\\.\\d+)?\\s*\\)\\s*$", "");
        return t.trim();
    }

    private static List<String> firstOccurrenceLatinLetters(String s) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                seen.add(String.valueOf(c).toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(seen);
    }

    private static List<String> splitWhereExplanations(String block) {
        List<String> parts = new ArrayList<>();
        for (String chunk : block.split("[;\\n]")) {
            String c = chunk.trim();
            if (!c.isEmpty()) {
                parts.add(c);
            }
        }
        return parts;
    }

    private static String extractLeadingSymbol(String part) {
        Matcher m = Pattern.compile("^([a-zA-Zα-ωΑ-Ω])\\s*[—–\\-]").matcher(part.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static List<String> checkUnitMixingAndNbsp(String fullScan, List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        String scan = fullScan != null ? fullScan : "";
        if (scan.isEmpty()) {
            for (ParagraphInfo p : paragraphs) {
                if (p.getText() != null) {
                    scan += p.getText() + "\n";
                }
            }
        }

        Set<String> usedRu = new LinkedHashSet<>();
        Set<String> usedSi = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : UNIT_RU_SI_PAIRS) {
            String ru = e.getKey();
            String si = e.getValue();
            if (unitAfterNumber(scan, ru)) {
                usedRu.add(ru);
            }
            if (unitAfterNumber(scan, si)) {
                usedSi.add(si);
            }
        }
        if (!usedRu.isEmpty() && !usedSi.isEmpty()) {
            issues.add(
                    String.format(
                            Locale.ROOT,
                            "ФТ-19: %s — в документе смешаны русские и международные обозначения единиц "
                                    + "(рус.: %s; межд.: %s). Как исправить: используйте везде один вариант по ГОСТ.",
                            REQ,
                            shortenList(usedRu, 8),
                            shortenList(usedSi, 8)));
        }

        Matcher nb = NUMBER_THEN_KNOWN_UNIT.matcher(scan);
        int nbspHits = 0;
        while (nb.find() && nbspHits < 8) {
            String sep = nb.group(2);
            if (sep != null && sep.indexOf(' ') >= 0) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "ФТ-19: %s — между числом и обозначением единицы измерения нужен неразрывный пробел, а не обычный "
                                        + "(фрагмент: «%s»). Как исправить: замените пробел на неразрывный (Ctrl+Shift+Space в Word).",
                                REQ,
                                shorten(nb.group(0), 50)));
                nbspHits++;
            }
        }

        return issues;
    }

    /** Обнаружить «число + пробел(ы) + единица» для пары рус/межд. */
    private static boolean unitAfterNumber(String scan, String unit) {
        if (unit == null || unit.isEmpty()) {
            return false;
        }
        String u = Pattern.quote(unit);
        Pattern p1 = Pattern.compile("\\d+[\\s\\u00A0]+" + u + "(?![а-яА-ЯёЁa-zA-Z])");
        return p1.matcher(scan).find();
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String shortenList(Set<String> set, int maxItems) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(s);
            i++;
            if (i >= maxItems) {
                sb.append("…");
                break;
            }
        }
        return sb.toString();
    }
}
