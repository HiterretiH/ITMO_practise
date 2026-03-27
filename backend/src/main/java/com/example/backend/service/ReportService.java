package com.example.backend.service;

import com.example.backend.json.DocumentInfo;
import com.example.backend.json.ErrorItem;
import com.example.backend.json.ErrorLocation;
import com.example.backend.json.ErrorSeverity;
import com.example.backend.json.ErrorType;
import com.example.backend.json.ReportOptions;
import com.example.backend.json.ValidationResult;
import com.example.backend.json.ValidationStatus;
import com.example.backend.json.ValidationSummary;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Chunk;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.RGBColor;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class ReportService {

    private static final float MARGIN = 36f;

    public byte[] buildReport(ValidationResult result, ReportOptions options) {
        if (result == null) {
            throw new IllegalArgumentException("validationData is required");
        }
        boolean includeRecommendations = includeRecommendations(options);
        try {
            return renderPdf(result, includeRecommendations);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate PDF report", e);
        }
    }

    private static boolean includeRecommendations(ReportOptions options) {
        if (options == null || options.getIncludeRecommendations() == null) {
            return true;
        }
        return Boolean.TRUE.equals(options.getIncludeRecommendations());
    }

    private byte[] renderPdf(ValidationResult result, boolean includeRecommendations) throws IOException {
        Font titleFont = loadFont(16, Font.BOLD);
        Font bodyFont = loadFont(11, Font.NORMAL);
        Font smallFont = loadFont(9, Font.NORMAL);
        Font sectionHeaderFont = loadFont(10, Font.BOLD);
        Font tableFont = loadFont(11, Font.NORMAL);
        Font tableFontBold = loadFont(11, Font.BOLD);
        Font tableHeaderFont = loadFont(11, Font.BOLD);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter.getInstance(document, out);
        document.open();

        document.add(new Paragraph("Отчёт о проверке шаблона ВКР", titleFont));
        document.add(new Paragraph("Автоматическая проверка шаблона на соответствие требованиям ИТМО", smallFont));
        document.add(new Paragraph(" "));

        DocumentInfo docInfo = result.getDocumentInfo();
        if (docInfo != null) {
            String name = nullToEmpty(docInfo.getFilename());
            if (!name.isEmpty()) {
                document.add(new Paragraph("Файл: " + name, bodyFont));
            }
            if (docInfo.getPages() != null) {
                document.add(new Paragraph("Страниц в документе: " + docInfo.getPages(), bodyFont));
            }
            if (docInfo.getParagraphsChecked() != null) {
                document.add(new Paragraph("Проверено абзацев: " + docInfo.getParagraphsChecked(), bodyFont));
            }
            document.add(new Paragraph(" "));
        }

        ValidationSummary summary = result.getSummary();
        if (summary != null) {
            document.add(new Paragraph("Сводка", sectionHeaderFont));
            Integer total = summary.getTotalErrors();
            Integer critical = summary.getCriticalErrors();
            ValidationStatus status = summary.getStatus();
            document.add(new Paragraph(
                    "Всего ошибок: " + (total != null ? total : 0)
                            + "; критических: " + (critical != null ? critical : 0)
                            + "; статус: " + statusLabel(status),
                    bodyFont));
            document.add(new Paragraph(" "));
        }

        List<ErrorItem> errors = result.getErrors();
        if (errors == null || errors.isEmpty()) {
            document.add(new Paragraph("Ошибок не найдено.", bodyFont));
        } else {
            document.add(new Paragraph("Найденные несоответствия", sectionHeaderFont));
            document.add(new Paragraph(" "));
            PdfPTable table = buildErrorTable(errors, includeRecommendations, tableFont, tableFontBold, tableHeaderFont);
            document.add(table);
        }

        document.close();
        return out.toByteArray();
    }

    private PdfPTable buildErrorTable(
            List<ErrorItem> errors,
            boolean includeRecommendations,
            Font tableFont,
            Font tableFontBold,
            Font tableHeaderFont
    ) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100f);
        try {
            table.setWidths(new float[]{0.45f, 1.2f, 3.15f, 1.0f});
        } catch (Exception ignored) {
            // keep default widths
        }

        addHeaderCell(table, "№", tableHeaderFont);
        addHeaderCell(table, "Критичность", tableHeaderFont);
        addHeaderCell(table, "Описание", tableHeaderFont);
        addHeaderCell(table, "Место", tableHeaderFont);

        int row = 1;
        for (ErrorItem item : errors) {
            table.addCell(numberCell(String.valueOf(row++), tableFont));
            table.addCell(severityCell(severityLabel(item.getSeverity()), tableFont));
            table.addCell(descriptionCell(item, includeRecommendations, tableFont, tableFontBold));
            table.addCell(placeCell(formatLocation(item.getLocation()), tableFont));
        }
        return table;
    }

    private PdfPCell descriptionCell(ErrorItem item, boolean includeRecommendation, Font font, Font titleFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(4);
        ErrorType type = item.getType();
        cell.addElement(new Paragraph(errorTypeRu(type), titleFont));
        String desc = nullToEmpty(item.getDescription());
        if (!desc.isEmpty()) {
            cell.addElement(new Paragraph(desc, font));
        }
        String rec = nullToEmpty(item.getRecommendation());
        if (includeRecommendation && !rec.isEmpty()) {
            Phrase recPhrase = new Phrase();
            recPhrase.add(new Chunk("Рекомендация: ", titleFont));
            recPhrase.add(new Chunk(rec, font));
            cell.addElement(new Paragraph(recPhrase));
        }
        return cell;
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new RGBColor(240, 240, 240));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static PdfPCell numberCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static PdfPCell severityCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static PdfPCell placeCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if ("—".equals(text)) {
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        }
        return cell;
    }

    private Font loadFont(float size, int style) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (is == null) {
                throw new IllegalStateException("Missing classpath resource: /fonts/DejaVuSans.ttf");
            }
            byte[] bytes = is.readAllBytes();
            try {
                BaseFont baseFont = BaseFont.createFont(
                        "DejaVuSans.ttf",
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED,
                        true,
                        bytes,
                        null
                );
                return new Font(baseFont, size, style);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create PDF font", e);
            }
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String statusLabel(ValidationStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case passed -> "пройдена";
            case warning -> "есть предупреждения";
            case failed -> "не пройдена";
        };
    }

    private static String severityLabel(ErrorSeverity severity) {
        if (severity == null) {
            return "—";
        }
        return severity == ErrorSeverity.critical ? "Высокая" : "Средняя";
    }

    private static String formatLocation(ErrorLocation loc) {
        if (loc == null) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        if (loc.getPage() != null) {
            sb.append("стр. ").append(loc.getPage());
        }
        if (loc.getParagraph() != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("абз. ").append(loc.getParagraph());
        }
        String el = loc.getElement();
        if (el != null && !el.isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(el);
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    private static String errorTypeRu(ErrorType type) {
        if (type == null) {
            return "—";
        }
        return switch (type) {
            case MISSING_SECTION -> "Отсутствует раздел";
            case FONT_MISMATCH -> "Неверный шрифт";
            case MARGIN_MISMATCH -> "Неверные поля страницы";
            case LINE_SPACING_ERROR -> "Неверный межстрочный интервал";
            case INDENT_ERROR -> "Неверный абзацный отступ";
            case HEADER_STYLE_ERROR -> "Оформление заголовка";
            case PAGE_NUMBER_MISSING -> "Нумерация страниц";
            case TABLE_CAPTION_ERROR -> "Подпись таблицы";
            case FIGURE_CAPTION_ERROR -> "Подпись рисунка";
            case CONTENT_TITLE_MISMATCH -> "Содержание / заголовки";
            case INVALID_LIST_FORMAT -> "Оформление списка";
            case FORMULA_ERROR -> "Оформление формулы";
            case CITATION_ERROR -> "Ссылки и источники";
        };
    }
}
