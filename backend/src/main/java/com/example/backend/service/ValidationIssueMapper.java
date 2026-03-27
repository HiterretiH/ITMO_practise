package com.example.backend.service;

import com.example.backend.json.ErrorItem;
import com.example.backend.json.ErrorLocation;
import com.example.backend.json.ErrorSeverity;
import com.example.backend.json.ErrorType;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ValidationIssueMapper {

    /** Начало сообщения: «ФТ-N:» или «ФТ-N (п. …):» (как в ряде проверок, напр. ФТ-11). */
    private static final Pattern FT_PREFIX = Pattern.compile("^ФТ-(\\d+)(?:\\s*\\([^)]*\\))?\\s*:\\s*");
    private static final Pattern LOC_ST_PAGE_PARA = Pattern.compile(
            "стр\\.\\s*(\\d+),\\s*абз\\.\\s*#(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOC_PARA_ONLY = Pattern.compile("абз\\.\\s*#(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_PREVIEW_SUFFIX = Pattern.compile(
            "\\s+Начало текста:\\s*.+$", Pattern.DOTALL);

    private static final Pattern EXPECTED_ACTUAL_SEMICOLON = Pattern.compile(
            "ожидается\\s+([^;]+);\\s*фактически\\s+([^.]+?)\\.", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EXPECTED_ACTUAL_ALIGN = Pattern.compile(
            "ожидается\\s+([^;]+);\\s*фактически\\s+([^;]+)\\s*\\(код", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ValidationIssueMapper() {
    }

    static ErrorItem toErrorItem(String message) {
        if (message == null || message.isBlank()) {
            return ErrorItem.builder()
                    .id(UUID.randomUUID())
                    .type(ErrorType.CONTENT_TITLE_MISMATCH)
                    .severity(ErrorSeverity.warning)
                    .description("")
                    .build();
        }
        String trimmed = message.trim();
        int ft = parseFtNumber(trimmed);
        ErrorType type = mapFtToErrorType(ft, trimmed);
        ErrorSeverity severity = inferSeverity(trimmed);
        ErrorLocation location = parseLocation(trimmed);

        String withoutPreview = TEXT_PREVIEW_SUFFIX.matcher(trimmed).replaceFirst("").trim();
        String previewFragment = extractPreviewFragment(trimmed);

        String noFt = stripFtPrefix(withoutPreview);
        ExpectedActualPair pair = parseExpectedActual(noFt);

        String description = capitalizeFirstLetter(noFt);
        String expected = pair.expected();
        String actual = pair.actual() != null ? pair.actual() : previewFragment;

        return ErrorItem.builder()
                .id(UUID.randomUUID())
                .type(type)
                .severity(severity)
                .location(location)
                .description(description)
                .expected(expected)
                .actual(actual)
                .recommendation(recommendationFor(type, noFt))
                .build();
    }

    private static String stripFtPrefix(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return FT_PREFIX.matcher(text.trim()).replaceFirst("").trim();
    }

    private static String capitalizeFirstLetter(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text.trim();
        }
        String t = text.trim();
        int cp = t.codePointAt(0);
        if (!Character.isLetter(cp) || Character.isUpperCase(cp)) {
            return t;
        }
        int upperCp = Character.toUpperCase(cp);
        StringBuilder sb = new StringBuilder(t.length());
        sb.appendCodePoint(upperCp);
        sb.append(t, t.offsetByCodePoints(0, 1), t.length());
        return sb.toString();
    }

    private static String extractPreviewFragment(String message) {
        Matcher m = Pattern.compile("Начало текста:\\s*(.+)$", Pattern.DOTALL).matcher(message);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private record ExpectedActualPair(String expected, String actual) {}

    private static ExpectedActualPair parseExpectedActual(String text) {
        Matcher m1 = EXPECTED_ACTUAL_SEMICOLON.matcher(text);
        if (m1.find()) {
            return new ExpectedActualPair(
                    m1.group(1).trim(),
                    m1.group(2).trim());
        }
        Matcher m2 = EXPECTED_ACTUAL_ALIGN.matcher(text);
        if (m2.find()) {
            return new ExpectedActualPair(
                    m2.group(1).trim(),
                    m2.group(2).trim());
        }
        return new ExpectedActualPair(null, null);
    }

    private static String recommendationFor(ErrorType type, String messageCore) {
        String m = messageCore == null ? "" : messageCore;
        if (type == ErrorType.LINE_SPACING_ERROR) {
            if (m.contains("выравнивание")) {
                return "В параметрах абзаца задайте выравнивание по ширине (п. 4.2).";
            }
            if (m.contains("межстрочный") || m.contains("интервал")) {
                return "В параметрах абзаца установите межстрочный интервал 1,5 (п. 4.2).";
            }
        }
        return switch (type) {
            case MISSING_SECTION ->
                    "Добавьте или восстановите требуемый раздел заголовком уровня 0 прописными буквами (п. 3.2, 4.4.1).";
            case CONTENT_TITLE_MISMATCH ->
                    "Согласуйте оглавление с заголовками в тексте и номерами страниц (п. 3.4).";
            case HEADER_STYLE_ERROR ->
                    "Приведите оформление заголовков к одному из допустимых вариантов методички (п. 4.4.4).";
            case FONT_MISMATCH ->
                    "Выделите текст и задайте Times New Roman, 12–14 pt, чёрный цвет для основного текста (п. 4.2).";
            case LINE_SPACING_ERROR ->
                    "В параметрах абзаца установите межстрочный интервал 1,5 (п. 4.2).";
            case INDENT_ERROR ->
                    "Задайте абзацный отступ (красную строку) 1,25 см (п. 4.2).";
            case MARGIN_MISMATCH ->
                    "Проверьте поля страницы: слева 30 мм, справа 10–15 мм, сверху и снизу по 20 мм (п. 4.2).";
            case PAGE_NUMBER_MISSING ->
                    "Вставьте номер страницы в нижний колонтитул по центру, без точки после номера (п. 4.3.1).";
            case FIGURE_CAPTION_ERROR ->
                    "Оформите подпись под рисунком по центру: «Рисунок N – …» (п. 4.5.3).";
            case TABLE_CAPTION_ERROR ->
                    "Оформите название таблицы над таблицей слева: «Таблица N – …» (п. 4.6.3).";
            case INVALID_LIST_FORMAT ->
                    "Приведите перечисление к единому стилю маркеров и отступов (приложение 1 к методичке).";
            case FORMULA_ERROR ->
                    "Проверьте выделение формулы, ссылки в тексте и оформление по п. 4.7.";
            case CITATION_ERROR ->
                    "Согласуйте ссылки в тексте со списком использованных источников (п. 4.10).";
        };
    }

    private static int parseFtNumber(String message) {
        Matcher m = FT_PREFIX.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static ErrorType mapFtToErrorType(int ft, String message) {
        return switch (ft) {
            case 4, 15, 16 -> ErrorType.MISSING_SECTION;
            case 5, 7 -> ErrorType.CONTENT_TITLE_MISMATCH;
            case 6 -> ErrorType.HEADER_STYLE_ERROR;
            case 8 -> ErrorType.FONT_MISMATCH;
            case 9 -> {
                if (message.contains("межстрочный") || message.contains("интервал") || message.contains("Интервал")) {
                    yield ErrorType.LINE_SPACING_ERROR;
                }
                if (message.contains("красная") || message.contains("отступ") || message.contains("Отступ")) {
                    yield ErrorType.INDENT_ERROR;
                }
                if (message.contains("выравнивание") || message.contains("Выравнивание")) {
                    yield ErrorType.LINE_SPACING_ERROR;
                }
                yield ErrorType.LINE_SPACING_ERROR;
            }
            case 10 -> ErrorType.MARGIN_MISMATCH;
            case 11 -> ErrorType.HEADER_STYLE_ERROR;
            case 12 -> ErrorType.PAGE_NUMBER_MISSING;
            case 13 -> ErrorType.FIGURE_CAPTION_ERROR;
            case 14 -> ErrorType.TABLE_CAPTION_ERROR;
            case 17 -> ErrorType.INVALID_LIST_FORMAT;
            case 18 -> ErrorType.CONTENT_TITLE_MISMATCH;
            case 19 -> ErrorType.FORMULA_ERROR;
            case 20 -> ErrorType.CITATION_ERROR;
            case 21 -> ErrorType.INVALID_LIST_FORMAT;
            default -> ErrorType.CONTENT_TITLE_MISMATCH;
        };
    }

    private static ErrorSeverity inferSeverity(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("рекомендуется") && lower.contains("крупнее нормы")) {
            return ErrorSeverity.warning;
        }
        return ErrorSeverity.critical;
    }

    private static ErrorLocation parseLocation(String message) {
        Matcher m1 = LOC_ST_PAGE_PARA.matcher(message);
        if (m1.find()) {
            return ErrorLocation.builder()
                    .page(Integer.parseInt(m1.group(1)))
                    .paragraph(Integer.parseInt(m1.group(2)))
                    .element(null)
                    .build();
        }
        Matcher m2 = LOC_PARA_ONLY.matcher(message);
        if (m2.find()) {
            return ErrorLocation.builder()
                    .page(null)
                    .paragraph(Integer.parseInt(m2.group(1)))
                    .element(null)
                    .build();
        }
        return null;
    }
}
