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
        Font headerFont = loadFont(10, Font.BOLD);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter.getInstance(document, out);
        document.open();

        document.add(new Paragraph("Отчёт о проверке ВКР", titleFont));
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
            document.add(new Paragraph("Сводка", headerFont));
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
            document.add(new Paragraph("Найденные несоответствия", headerFont));
            document.add(new Paragraph(" "));
            PdfPTable table = buildErrorTable(errors, includeRecommendations, bodyFont, smallFont, headerFont);
            document.add(table);
        }

        document.close();
        return out.toByteArray();
    }

    private PdfPTable buildErrorTable(
            List<ErrorItem> errors,
            boolean includeRecommendations,
            Font bodyFont,
            Font smallFont,
            Font headerFont
    ) {
        int cols = includeRecommendations ? 7 : 6;
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100f);
        try {
            if (includeRecommendations) {
                table.setWidths(new float[]{0.55f, 1.0f, 2.0f, 1.1f, 1.1f, 1.1f, 1.35f});
            } else {
                table.setWidths(new float[]{0.55f, 1.0f, 2.2f, 1.2f, 1.2f, 1.25f});
            }
        } catch (Exception ignored) {
            // keep default widths
        }

        addHeaderCell(table, "№", headerFont);
        addHeaderCell(table, "Уровень", headerFont);
        addHeaderCell(table, "Тип и описание", headerFont);
        addHeaderCell(table, "Место", headerFont);
        addHeaderCell(table, "Ожидаемое", headerFont);
        addHeaderCell(table, "Фактическое", headerFont);
        if (includeRecommendations) {
            addHeaderCell(table, "Рекомендация", headerFont);
        }

        int row = 1;
        for (ErrorItem item : errors) {
            boolean critical = item.getSeverity() == ErrorSeverity.critical;
            PdfPCell c0 = dataCell(String.valueOf(row++), bodyFont, critical);
            PdfPCell c1 = dataCell(severityLabel(item.getSeverity()), bodyFont, critical);

            PdfPCell typeCell = new PdfPCell();
            typeCell.setPadding(4);
            if (critical) {
                typeCell.setBackgroundColor(new RGBColor(255, 230, 230));
            }
            ErrorType type = item.getType();
            typeCell.addElement(new Paragraph(errorTypeRu(type), bodyFont));
            String desc = nullToEmpty(item.getDescription());
            if (!desc.isEmpty()) {
                typeCell.addElement(new Paragraph(desc, smallFont));
            }
            table.addCell(c0);
            table.addCell(c1);
            table.addCell(typeCell);

            table.addCell(dataCell(formatLocation(item.getLocation()), bodyFont, critical));
            table.addCell(dataCell(nullToEmpty(item.getExpected()), bodyFont, critical));
            table.addCell(dataCell(nullToEmpty(item.getActual()), bodyFont, critical));
            if (includeRecommendations) {
                table.addCell(dataCell(nullToEmpty(item.getRecommendation()), bodyFont, critical));
            }
        }
        return table;
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new RGBColor(240, 240, 240));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static PdfPCell dataCell(String text, Font font, boolean critical) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        if (critical) {
            cell.setBackgroundColor(new RGBColor(255, 230, 230));
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
        return severity == ErrorSeverity.critical ? "критическая" : "предупреждение";
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
