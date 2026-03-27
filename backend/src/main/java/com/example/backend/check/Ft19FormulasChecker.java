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
 * оформление формул и уравнений (п. 4.7).
 * <p>
 * <b>Что считается формулой:</b> только абзацы, для которых {@link com.example.backend.util.OfficeMathDetector}
 * находит в XML OMML-узлы ({@code m:oMath} / {@code m:oMathPara} в {@code word/document.xml} и т.д.).
 * См. {@link com.example.backend.util.OfficeMathDetector#OMML_NAMESPACE_URI}. Поле
 * {@link com.example.backend.domain.ParagraphInfo#isContainsFormula()} задаётся в {@code DocxLoadService} по этому признаку.
 * Обычный текст, номера разделов («1 Введение»), строки оглавления — <em>не</em> формулы и не проверяются как таковые.
 * <p>
 * Проверки ниже применяются <b>только к абзацам с OMML-формулой</b> ({@code containsFormula}), кроме ссылки на номер:
 * ссылка ищется в остальном тексте документа. Единицы и неразрывный пробел — только внутри текста абзацев с формулами.
 * Дополнительно: формула в отдельной строке — в абзаце не должно быть русскоязычного «простого» текста, кроме хвоста «, где …»
 * после формулы (п. 4.7 / ТЗ).
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

    /**
     * @param paragraphs абзацы документа; признак формулы — {@link ParagraphInfo#isContainsFormula()}
     */
    public static List<String> check(List<ParagraphInfo> paragraphs) {
        List<String> issues = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return issues;
        }

        List<Integer> formulaIndices = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (paragraphs.get(i).isContainsFormula()) {
                formulaIndices.add(i);
            }
        }
        if (formulaIndices.isEmpty()) {
            return issues;
        }

        String formulaOnlyScan = joinFormulaParagraphTexts(paragraphs);
        issues.addAll(checkUnitMixingAndNbspInFormulaText(formulaOnlyScan, paragraphs, formulaIndices));

        String textForRefs = buildTextExcludingFormulaParagraphs(paragraphs);

        int formulaOrdinal = 0;
        for (int fi : formulaIndices) {
            formulaOrdinal++;
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
                                    "%s — формула №%d (абзац №%d по порядку в документе, оценка стр. %s): "
                                            + "в конце строки указан номер (%s), но в остальном тексте работы нет ссылки на эту формулу в виде «(%s)». "
                                            + "По п. 4.7 каждая пронумерованная формула должна быть упомянута в тексте со своим номером в круглых скобках. "
                                            + "Как исправить: в нужном месте текста вставьте ссылку, например: «… согласно (%s) …».",
                                    REQ,
                                    formulaOrdinal,
                                    fi + 1,
                                    formatPage(fp.getPageIndex()),
                                    num,
                                    num,
                                    num));
                }
            }

            issues.addAll(checkFormulaOnSeparateLine(raw, fp, formulaOrdinal, fi));
            issues.addAll(checkBlankLinesAroundFormula(paragraphs, fi, formulaOrdinal));
            issues.addAll(checkGdeAndExplanations(paragraphs, fi, raw, formulaOrdinal));

            if (issues.size() >= MAX_ISSUES) {
                return issues;
            }
        }

        formulaOrdinal = 0;
        for (int fi : formulaIndices) {
            formulaOrdinal++;
            issues.addAll(checkSymbolOrder(paragraphs, fi, formulaOrdinal));
            if (issues.size() >= MAX_ISSUES) {
                return issues;
            }
        }

        return issues;
    }

    private static String joinFormulaParagraphTexts(List<ParagraphInfo> paragraphs) {
        StringBuilder sb = new StringBuilder();
        for (ParagraphInfo p : paragraphs) {
            if (p.isContainsFormula() && p.getText() != null) {
                sb.append(p.getText()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String formatPage(Integer page) {
        return page == null ? "не определена" : page.toString();
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

    /**
     * П. 4.7: формула выделяется в отдельную строку. Эвристика: после удаления номера {@code (n)} и хвоста «, где …»
     * не должно оставаться кириллического текста (два и более подряд идущих букв) — иначе, вероятно, обычный текст в том же абзаце.
     */
    private static List<String> checkFormulaOnSeparateLine(
            String raw, ParagraphInfo fp, int formulaOrdinal, int paragraphIndex0) {
        List<String> issues = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return issues;
        }
        String t = raw.replace('\u00A0', ' ').trim();
        t = t.replaceAll("(?i),\\s*где\\s.*$", "").trim();
        t = stripTrailingNumber(t);
        if (t.isEmpty()) {
            return issues;
        }
        if (Pattern.compile("[а-яА-ЯёЁ]{2,}").matcher(t).find()) {
            issues.add(
                    String.format(
                            Locale.ROOT,
                            "%s — формула №%d (абзац документа №%d, стр. %s): по п. 4.7 формула должна быть выделена в отдельную строку: "
                                    + "в абзаце с OMML не должно оставаться связного русскоязычного текста, кроме продолжения «, где …» в конце строки формулы. "
                                    + "После отделения пояснения «где» и номера формулы в тексте абзаца остаётся: «%s». "
                                    + "Как исправить: оставьте в абзаце только формулу и номер (и при необходимости «, где …») или вынесите пояснение в соседний абзац.",
                            REQ,
                            formulaOrdinal,
                            paragraphIndex0 + 1,
                            formatPage(fp.getPageIndex()),
                            shorten(t, 100)));
        }
        return issues;
    }

    private static List<String> checkBlankLinesAroundFormula(List<ParagraphInfo> paragraphs, int i, int formulaOrdinal) {
        List<String> issues = new ArrayList<>();
        ParagraphInfo fp = paragraphs.get(i);
        int docPara = i + 1;
        String pageStr = formatPage(fp.getPageIndex());
        if (i > 0) {
            ParagraphInfo prev = paragraphs.get(i - 1);
            if (!isBlankParagraph(prev)) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "%s — формула №%d (абзац документа №%d, стр. %s): перед формулой должна быть пустая строка "
                                        + "(отдельное выделение формулы по п. 4.7). Сейчас сразу выше идёт непустой абзац без пустой строки между ним и формулой. "
                                        + "Как исправить: вставьте пустой абзац между предыдущим текстом и строкой с формулой.",
                                REQ,
                                formulaOrdinal,
                                docPara,
                                pageStr));
            }
        }
        if (i + 1 < paragraphs.size()) {
            ParagraphInfo next = paragraphs.get(i + 1);
            String nt = safeTrim(next.getText());
            if (!isBlankParagraph(next) && !startsWithGde(nt) && !isGdeContinuationOk(paragraphs, i)) {
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "%s — формула №%d (абзац документа №%d, стр. %s): после формулы ожидается пустая строка "
                                        + "или абзац с пояснениями к символам, начинающийся со слова «где» (если пояснения не продолжаются в той же строке через «, где …»). "
                                        + "Сейчас следующий абзац (№%d) не пустой и не начинается с «где». "
                                        + "Как исправить: вставьте пустой абзац после формулы или начните блок пояснений с «где …».",
                                REQ,
                                formulaOrdinal,
                                docPara,
                                pageStr,
                                i + 2));
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

    private static List<String> checkGdeAndExplanations(List<ParagraphInfo> paragraphs, int i, String formulaRaw, int formulaOrdinal) {
        List<String> issues = new ArrayList<>();
        ParagraphInfo fp = paragraphs.get(i);
        int docPara = i + 1;
        String pageStr = formatPage(fp.getPageIndex());
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
                            "%s — формула №%d (абзац документа №%d, стр. %s): первый непустой абзац после формулы (№%d) выглядит как расшифровка символов "
                                    + "(есть «символ — пояснение»), но не начинается со слова «где». По п. 4.7 пояснения к символам из формулы должны вводиться словом «где» "
                                    + "(в новой строке после пустой или через «, где» в конце строки формулы). "
                                    + "Как исправить: начните этот абзац с «где …» или перенесите расшифровки в строку с формулой после «, где …».",
                            REQ,
                            formulaOrdinal,
                            docPara,
                            pageStr,
                            j + 1));
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
    private static List<String> checkSymbolOrder(List<ParagraphInfo> paragraphs, int formulaIndex, int formulaOrdinal) {
        List<String> issues = new ArrayList<>();
        ParagraphInfo fp = paragraphs.get(formulaIndex);
        String raw = fp.getText();
        if (raw == null) {
            return issues;
        }
        int docPara = formulaIndex + 1;
        String pageStr = formatPage(fp.getPageIndex());
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
                                "%s — формула №%d (абзац документа №%d, стр. %s): порядок расшифровки символов после «где» не совпадает с порядком "
                                        + "первых латинских букв в тексте формулы (без номера в скобках). На позиции %d в блоке пояснений ожидался символ «%s» "
                                        + "(как в формуле), фактически указан «%s». "
                                        + "Как исправить: переставьте фрагменты расшифровки так, чтобы они шли в том же порядке, что и соответствующие буквы в формуле.",
                                REQ,
                                formulaOrdinal,
                                docPara,
                                pageStr,
                                k + 1,
                                lettersInFormula.get(k),
                                explained.get(k)));
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

    /**
     * Единицы и NBSP — только по объединённому тексту абзацев с OMML-формулами (не по всему документу).
     */
    private static List<String> checkUnitMixingAndNbspInFormulaText(
            String formulaScan, List<ParagraphInfo> paragraphs, List<Integer> formulaIndices) {
        List<String> issues = new ArrayList<>();
        String scan = formulaScan != null ? formulaScan : "";
        if (scan.isEmpty()) {
            return issues;
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
                            "%s — в абзацах с формулами (OMML) одновременно встречаются русские и международные обозначения единиц измерения "
                                    + "(рус.: %s; межд.: %s). Проверка выполняется только по тексту таких абзацев (всего абзацев с формулой: %d). "
                                    + "Как исправить: выберите один стиль обозначений (русские или SI) и примените его ко всем числам с единицами в формульных строках.",
                            REQ,
                            shortenList(usedRu, 8),
                            shortenList(usedSi, 8),
                            formulaIndices.size()));
        }

        Matcher nb = NUMBER_THEN_KNOWN_UNIT.matcher(scan);
        int nbspHits = 0;
        while (nb.find() && nbspHits < 8) {
            String sep = nb.group(2);
            if (sep != null && sep.indexOf(' ') >= 0) {
                String frag = shorten(nb.group(0), 50);
                int ord = findFormulaOrdinalContaining(nb.group(0), paragraphs, formulaIndices);
                String loc =
                        ord > 0
                                ? String.format(
                                        Locale.ROOT,
                                        "формула №%d (абзац документа №%d, стр. %s)",
                                        ord,
                                        formulaIndices.get(ord - 1) + 1,
                                        formatPage(paragraphs.get(formulaIndices.get(ord - 1)).getPageIndex()))
                                : "в одном из абзацев с формулой";
                issues.add(
                        String.format(
                                Locale.ROOT,
                                "%s — %s: между числом и обозначением единицы из известного перечня нужен неразрывный пробел (U+00A0), а не обычный пробел "
                                        + "(фрагмент в тексте формульного абзаца: «%s»). "
                                        + "Как исправить: в Word поставьте курсор между числом и единицей и вставьте неразрывный пробел (Ctrl+Shift+Space).",
                                REQ,
                                loc,
                                frag));
                nbspHits++;
            }
        }

        return issues;
    }

    /** В каком по счёту фрагменте «число + единица» встретилась строка (по вхождению подстроки в текст абзаца). */
    private static int findFormulaOrdinalContaining(String match, List<ParagraphInfo> paragraphs, List<Integer> formulaIndices) {
        for (int o = 0; o < formulaIndices.size(); o++) {
            String t = paragraphs.get(formulaIndices.get(o)).getText();
            if (t != null && t.contains(match)) {
                return o + 1;
            }
        }
        return 0;
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
