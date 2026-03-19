package com.example.backend.service;

import com.example.backend.exception.ValidationException;
import com.example.backend.model.domain.*;
import com.example.backend.util.DocumentFileValidator;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.apache.xmlbeans.XmlObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DocxLoadService {

    private static final double TWIPS_TO_CM = 1.0 / 567.0;
    private static final Pattern TABLE_CAPTION_PATTERN = Pattern.compile("^\\s*таблица\\b.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FIGURE_CAPTION_PATTERN = Pattern.compile("^\\s*рисунок\\b.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int CAPTION_LINK_DISTANCE = 2;
    public DocumentStructure load(String filename, String contentType, InputStream inputStream) {
        DocumentFileValidator.validate(filename, contentType);
        if (DocumentFileValidator.isDocx(filename)) {
            return parseDocx(inputStream);
        }
        if (DocumentFileValidator.isDoc(filename)) {
            return parseDoc(inputStream);
        }
        throw new ValidationException("Недопустимый формат файла.");
    }

    private DocumentStructure parseDocx(InputStream inputStream) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            List<ParagraphInfo> paragraphs = new ArrayList<>();
            List<TableInfo> tableInfos = new ArrayList<>();
            List<FigureInfo> figureInfos = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();
            List<BodyParagraphMeta> bodyParagraphs = new ArrayList<>();
            List<CaptionCandidate> captions = new ArrayList<>();

            int paraIndex = 0;
            int bodyIndex = 0;
            Iterator<IBodyElement> it = doc.getBodyElementsIterator();
            while (it.hasNext()) {
                IBodyElement element = it.next();
                if (element instanceof XWPFParagraph xp) {
                    ParagraphInfo info = mapParagraph(xp, null);
                    paragraphs.add(info);
                    String paragraphText = sanitizeText(info.getText());
                    bodyParagraphs.add(new BodyParagraphMeta(bodyIndex, paraIndex, paragraphText, hasPictures(xp)));
                    CaptionType captionType = detectCaptionType(paragraphText);
                    if (captionType != null) {
                        captions.add(new CaptionCandidate(captionType, paragraphText, bodyIndex, paraIndex));
                    }
                    if (info.getText() != null && !info.getText().isBlank()) {
                        fullText.append(info.getText()).append("\n");
                    }
                    paraIndex++;
                } else if (element instanceof XWPFTable table) {
                    String tableCaption = findNearbyTableCaption(bodyParagraphs, captions, paraIndex);
                    tableInfos.add(TableInfo.builder()
                            .caption(tableCaption)
                            .paragraphIndex(paraIndex)
                            .pageIndex(0)
                            .build());
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph p : cell.getParagraphs()) {
                                ParagraphInfo pi = mapParagraph(p, null);
                                paragraphs.add(pi);
                                if (pi.getText() != null) fullText.append(pi.getText()).append(" ");
                                paraIndex++;
                            }
                        }
                    }
                }
                bodyIndex++;
            }

            populateFigureInfos(figureInfos, bodyParagraphs);
            linkCaptionsToTables(captions, tableInfos, bodyParagraphs);
            linkCaptionsToFigures(captions, figureInfos, bodyParagraphs);

            PageMargins margins = extractMargins(doc);
            return DocumentStructure.builder()
                    .paragraphs(paragraphs)
                    .margins(margins)
                    .tables(tableInfos)
                    .figures(figureInfos)
                    .fullText(fullText.toString().trim())
                    .format("docx")
                    .build();
        } catch (IOException e) {
            throw new ValidationException("Не удалось прочитать документ .docx: " + e.getMessage());
        }
    }

    private ParagraphInfo mapParagraph(XWPFParagraph xp, Integer pageIndex) {
        String text = xp.getText();
        ParagraphStyleSnapshot style = new ParagraphStyleSnapshot();

        if (xp.getAlignment() != null) {
            style.alignment = xp.getAlignment().name();
        }
        if (xp.getCTP().getPPr() != null) {
            if (xp.getCTP().getPPr().getSpacing() != null) {
                BigInteger line = toBigInteger(xp.getCTP().getPPr().getSpacing().getLine());
                if (line != null) style.lineSpacing = line.intValue() / 240.0;
            }
            if (xp.getCTP().getPPr().getInd() != null) {
                BigInteger fl = toBigInteger(xp.getCTP().getPPr().getInd().getFirstLine());
                BigInteger left = toBigInteger(xp.getCTP().getPPr().getInd().getLeft());
                if (fl != null) style.firstLineIndentCm = fl.intValue() * TWIPS_TO_CM;
                if (left != null) style.leftIndentCm = left.intValue() * TWIPS_TO_CM;
            }
        }

        int runCount = 0;
        boolean anyBold = false;
        boolean anyItalic = false;
        Map<String, Integer> fontCounts = new HashMap<>();
        Map<Double, Integer> sizeCounts = new HashMap<>();
        Map<String, Integer> colorCounts = new HashMap<>();
        for (XWPFRun run : xp.getRuns()) {
            runCount++;
            String runFont = safeFontName(run);
            if (runFont != null) fontCounts.merge(runFont, 1, Integer::sum);

            Double runSize = safeFontSize(run);
            if (runSize != null) sizeCounts.merge(runSize, 1, Integer::sum);

            String runColor = safeColor(run);
            if (runColor != null) colorCounts.merge(runColor, 1, Integer::sum);

            anyBold = anyBold || run.isBold();
            anyItalic = anyItalic || run.isItalic();
        }
        if (runCount > 0) {
            style.bold = anyBold;
            style.italic = anyItalic;
            style.fontName = mostFrequent(fontCounts);
            style.fontSizePt = mostFrequent(sizeCounts);
            style.colorHex = mostFrequent(colorCounts);
        }

        applyStyleFallbacks(xp, style);
        normalizeStyleValues(style, text);

        return ParagraphInfo.builder()
                .text(text)
                .fontName(style.fontName)
                .fontSizePt(style.fontSizePt)
                .bold(style.bold)
                .italic(style.italic)
                .colorHex(style.colorHex)
                .alignment(style.alignment)
                .lineSpacing(style.lineSpacing)
                .firstLineIndentCm(style.firstLineIndentCm)
                .leftIndentCm(style.leftIndentCm)
                .pageIndex(pageIndex)
                .build();
    }

    private void applyStyleFallbacks(XWPFParagraph paragraph, ParagraphStyleSnapshot out) {
        if (paragraph.getDocument() == null || paragraph.getDocument().getStyles() == null) return;
        applyParagraphStyleFallback(paragraph, out);
        applyDocDefaultsFallback(paragraph, out);
    }

    private void applyParagraphStyleFallback(XWPFParagraph paragraph, ParagraphStyleSnapshot out) {
        String styleId = paragraph.getStyleID();
        if (styleId == null || styleId.isBlank()) return;
        XWPFStyle style = paragraph.getDocument().getStyles().getStyle(styleId);
        if (style == null || style.getCTStyle() == null) return;
        applyStyleChainFallback(paragraph.getDocument().getStyles(), style, out, new HashSet<>());
    }

    private void applyStyleChainFallback(XWPFStyles styles, XWPFStyle style, ParagraphStyleSnapshot out, Set<String> visited) {
        if (style == null || style.getCTStyle() == null) return;
        String styleId = style.getStyleId();
        if (styleId != null && !styleId.isBlank()) {
            if (visited.contains(styleId)) return;
            visited.add(styleId);
        }

        CTStyle ctStyle = style.getCTStyle();
        XmlObject styleXml = ctStyle;
        String xml = styleXml.xmlText();

        if (out.fontName == null) {
            String font = xmlAttrFromTag(xml, "rFonts", "ascii");
            if (font == null) font = xmlAttrFromTag(xml, "rFonts", "hAnsi");
            if (font != null && !font.isBlank()) out.fontName = font;
        }
        if (out.fontSizePt == null) {
            String sz = xmlAttrFromTag(xml, "sz", "val");
            Double pt = halfPointsToPt(sz);
            if (pt != null) out.fontSizePt = pt;
        }
        if (out.bold == null) {
            out.bold = xmlOnOffFromTag(xml, "b");
        }
        if (out.italic == null) {
            out.italic = xmlOnOffFromTag(xml, "i");
        }
        if (out.colorHex == null) {
            String color = xmlAttrFromTag(xml, "color", "val");
            if (color != null && !color.isBlank()) out.colorHex = color;
        }

        if (out.alignment == null) {
            String jc = xmlAttrFromTag(xml, "jc", "val");
            if (jc != null && !jc.isBlank()) out.alignment = jc.toUpperCase(Locale.ROOT);
        }
        if (out.lineSpacing == null) {
            String line = xmlAttrFromTag(xml, "spacing", "line");
            BigInteger v = toBigInteger(line);
            if (v != null) out.lineSpacing = v.intValue() / 240.0;
        }
        if (out.firstLineIndentCm == null) {
            String fl = xmlAttrFromTag(xml, "ind", "firstLine");
            BigInteger v = toBigInteger(fl);
            if (v != null) out.firstLineIndentCm = v.intValue() * TWIPS_TO_CM;
        }
        if (out.leftIndentCm == null) {
            String left = xmlAttrFromTag(xml, "ind", "left");
            BigInteger v = toBigInteger(left);
            if (v != null) out.leftIndentCm = v.intValue() * TWIPS_TO_CM;
        }

        String parentStyleId = extractBasedOnStyleId(xml);
        if (parentStyleId != null && styles != null) {
            XWPFStyle parent = styles.getStyle(parentStyleId);
            applyStyleChainFallback(styles, parent, out, visited);
        }
    }

    private void applyDocDefaultsFallback(XWPFParagraph paragraph, ParagraphStyleSnapshot out) {
        XmlObject ctStyles = resolveCtStyles(paragraph.getDocument().getStyles());
        if (ctStyles == null) return;
        String xml = ctStyles.xmlText();

        if (out.fontName == null) {
            String font = xmlAttrInDocDefaults(xml, "rFonts", "ascii");
            if (font == null) font = xmlAttrInDocDefaults(xml, "rFonts", "hAnsi");
            if (font != null && !font.isBlank()) out.fontName = font;
        }
        if (out.fontSizePt == null) {
            String sz = xmlAttrInDocDefaults(xml, "sz", "val");
            Double pt = halfPointsToPt(sz);
            if (pt != null) out.fontSizePt = pt;
        }
        if (out.bold == null) {
            out.bold = xmlOnOffInDocDefaults(xml, "b");
        }
        if (out.italic == null) {
            out.italic = xmlOnOffInDocDefaults(xml, "i");
        }
        if (out.colorHex == null) {
            String color = xmlAttrInDocDefaults(xml, "color", "val");
            if (color != null && !color.isBlank()) out.colorHex = color;
        }

        if (out.alignment == null) {
            String jc = xmlAttrInDocDefaults(xml, "jc", "val");
            if (jc != null && !jc.isBlank()) out.alignment = jc.toUpperCase(Locale.ROOT);
        }
        if (out.lineSpacing == null) {
            String line = xmlAttrInDocDefaults(xml, "spacing", "line");
            BigInteger v = toBigInteger(line);
            if (v != null) out.lineSpacing = v.intValue() / 240.0;
        }
        if (out.firstLineIndentCm == null) {
            String fl = xmlAttrInDocDefaults(xml, "ind", "firstLine");
            BigInteger v = toBigInteger(fl);
            if (v != null) out.firstLineIndentCm = v.intValue() * TWIPS_TO_CM;
        }
        if (out.leftIndentCm == null) {
            String left = xmlAttrInDocDefaults(xml, "ind", "left");
            BigInteger v = toBigInteger(left);
            if (v != null) out.leftIndentCm = v.intValue() * TWIPS_TO_CM;
        }
    }

    private PageMargins extractMargins(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
        if (sectPr == null) return null;
        CTPageMar pgMar = sectPr.getPgMar();
        if (pgMar == null) return null;
        return PageMargins.builder()
                .leftCm(twipsToCm(pgMar.getLeft()))
                .rightCm(twipsToCm(pgMar.getRight()))
                .topCm(twipsToCm(pgMar.getTop()))
                .bottomCm(twipsToCm(pgMar.getBottom()))
                .build();
    }

    private static double twipsToCm(Object twips) {
        BigInteger value = toBigInteger(twips);
        if (value == null) return 0;
        return value.longValue() * TWIPS_TO_CM;
    }

    private static BigInteger toBigInteger(Object value) {
        if (value == null) return null;
        if (value instanceof BigInteger bi) return bi;
        if (value instanceof Number n) return BigInteger.valueOf(n.longValue());
        try {
            return new BigInteger(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double safeFontSize(XWPFRun run) {
        int size = run.getFontSize();
        if (size > 0) return (double) size;
        return null;
    }

    private static String safeFontName(XWPFRun run) {
        String v = run.getFontName();
        if (v != null && !v.isBlank()) return v;
        try {
            String family = run.getFontFamily();
            if (family != null && !family.isBlank()) return family;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String safeColor(XWPFRun run) {
        String v = run.getColor();
        if (v == null || v.isBlank()) return null;
        return v;
    }

    private static <T> T mostFrequent(Map<T, Integer> counts) {
        if (counts.isEmpty()) return null;
        T best = null;
        int bestCount = -1;
        for (Map.Entry<T, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    private static XmlObject resolveCtStyles(XWPFStyles styles) {
        try {
            var method = styles.getClass().getMethod("getCTStyles");
            Object value = method.invoke(styles);
            if (value instanceof XmlObject xml) return xml;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Boolean xmlOnOffFromTag(String xml, String tag) {
        String val = xmlAttrFromTag(xml, tag, "val");
        if (xml == null || !containsTag(xml, tag)) return null;
        if (val == null || val.isBlank()) return true;
        String normalized = val.trim().toLowerCase(Locale.ROOT);
        return !("0".equals(normalized) || "false".equals(normalized) || "off".equals(normalized));
    }

    private static String xmlAttrFromTag(String xml, String tag, String attr) {
        if (xml == null) return null;
        Pattern p = Pattern.compile("<w:" + Pattern.quote(tag) + "\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        var m = p.matcher(xml);
        if (!m.find()) return null;
        String attrs = m.group(1);
        if (attrs == null) return null;
        Pattern ap = Pattern.compile("w:" + Pattern.quote(attr) + "=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        var am = ap.matcher(attrs);
        if (!am.find()) return null;
        return am.group(1);
    }

    private static boolean containsTag(String xml, String tag) {
        if (xml == null) return false;
        return Pattern.compile("<w:" + Pattern.quote(tag) + "\\b", Pattern.CASE_INSENSITIVE).matcher(xml).find();
    }

    private static String xmlAttrInDocDefaults(String xml, String tag, String attr) {
        String defaults = extractDocDefaults(xml);
        if (defaults == null) return null;
        return xmlAttrFromTag(defaults, tag, attr);
    }

    private static Boolean xmlOnOffInDocDefaults(String xml, String tag) {
        String defaults = extractDocDefaults(xml);
        if (defaults == null) return null;
        return xmlOnOffFromTag(defaults, tag);
    }

    private static String extractDocDefaults(String xml) {
        if (xml == null) return null;
        Pattern p = Pattern.compile("<w:docDefaults\\b[\\s\\S]*?</w:docDefaults>", Pattern.CASE_INSENSITIVE);
        var m = p.matcher(xml);
        if (!m.find()) return null;
        return m.group();
    }

    private static Double halfPointsToPt(String halfPoints) {
        BigInteger v = toBigInteger(halfPoints);
        if (v == null) return null;
        return v.doubleValue() / 2.0;
    }

    private static String extractBasedOnStyleId(String xml) {
        if (xml == null) return null;
        String id = xmlAttrFromTag(xml, "basedOn", "val");
        if (id == null || id.isBlank()) return null;
        return id.trim();
    }

    private static void normalizeStyleValues(ParagraphStyleSnapshot style, String text) {
        if (style.colorHex != null) {
            if ("auto".equalsIgnoreCase(style.colorHex)) {
                style.colorHex = "000000";
            }
            style.colorHex = style.colorHex.toUpperCase(Locale.ROOT);
        }
        if (style.bold == null && isBlankText(text)) style.bold = false;
        if (style.italic == null && isBlankText(text)) style.italic = false;
    }

    private static boolean isBlankText(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static String findNearbyTableCaption(
            List<BodyParagraphMeta> bodyParagraphs,
            List<CaptionCandidate> captions,
            int tableParagraphIndex
    ) {
        CaptionCandidate best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (CaptionCandidate c : captions) {
            if (c.type != CaptionType.TABLE) continue;
            if (c.paragraphIndex > tableParagraphIndex) continue;
            int dist = tableParagraphIndex - c.paragraphIndex;
            if (dist <= CAPTION_LINK_DISTANCE && dist < bestDistance) {
                best = c;
                bestDistance = dist;
            }
        }
        return best == null ? null : best.text;
    }

    private DocumentStructure parseDoc(InputStream inputStream) {
        try (HWPFDocument doc = new HWPFDocument(inputStream)) {
            Range range = doc.getRange();
            List<ParagraphInfo> paragraphs = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();

            for (int i = 0; i < range.numParagraphs(); i++) {
                Paragraph p = range.getParagraph(i);
                String text = p.text().trim();
                fullText.append(text).append("\n");
                paragraphs.add(ParagraphInfo.builder()
                        .text(text)
                        .pageIndex(0)
                        .build());
            }

            return DocumentStructure.builder()
                    .paragraphs(paragraphs)
                    .tables(List.of())
                    .figures(List.of())
                    .fullText(fullText.toString().trim())
                    .format("doc")
                    .build();
        } catch (IOException e) {
            throw new ValidationException("Не удалось прочитать документ .doc: " + e.getMessage());
        }
    }

    private void populateFigureInfos(List<FigureInfo> figureInfos, List<BodyParagraphMeta> bodyParagraphs) {
        for (BodyParagraphMeta p : bodyParagraphs) {
            if (p.hasPicture) {
                figureInfos.add(FigureInfo.builder()
                        .caption(null)
                        .paragraphIndex(p.paragraphIndex)
                        .pageIndex(0)
                        .build());
            }
        }
    }

    private void linkCaptionsToTables(List<CaptionCandidate> captions, List<TableInfo> tables, List<BodyParagraphMeta> bodyParagraphs) {
        for (CaptionCandidate c : captions) {
            if (c.type != CaptionType.TABLE) continue;
            BodyParagraphMeta paragraphMeta = findParagraphMeta(bodyParagraphs, c.bodyIndex);
            if (paragraphMeta == null) continue;

            List<TableInfo> candidates = tables.stream()
                    .filter(t -> Math.abs(t.getParagraphIndex() - paragraphMeta.paragraphIndex) <= CAPTION_LINK_DISTANCE)
                    .filter(t -> t.getCaption() == null)
                    .sorted(Comparator.comparingInt(t -> Math.abs(t.getParagraphIndex() - paragraphMeta.paragraphIndex)))
                    .toList();

            if (candidates.size() == 1) {
                candidates.get(0).setCaption(c.text);
            }
        }
    }

    private void linkCaptionsToFigures(List<CaptionCandidate> captions, List<FigureInfo> figures, List<BodyParagraphMeta> bodyParagraphs) {
        for (CaptionCandidate c : captions) {
            if (c.type != CaptionType.FIGURE) continue;
            BodyParagraphMeta paragraphMeta = findParagraphMeta(bodyParagraphs, c.bodyIndex);
            if (paragraphMeta == null) continue;

            List<FigureInfo> candidates = figures.stream()
                    .filter(f -> Math.abs(f.getParagraphIndex() - paragraphMeta.paragraphIndex) <= CAPTION_LINK_DISTANCE)
                    .filter(f -> f.getCaption() == null)
                    .sorted(Comparator.comparingInt(f -> Math.abs(f.getParagraphIndex() - paragraphMeta.paragraphIndex)))
                    .toList();

            if (candidates.size() == 1) {
                candidates.get(0).setCaption(c.text);
            }
        }
    }

    private static BodyParagraphMeta findParagraphMeta(List<BodyParagraphMeta> bodyParagraphs, int bodyIndex) {
        for (BodyParagraphMeta p : bodyParagraphs) {
            if (p.bodyIndex == bodyIndex) return p;
        }
        return null;
    }

    private static boolean hasPictures(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (!run.getEmbeddedPictures().isEmpty()) return true;
        }
        return false;
    }

    private static CaptionType detectCaptionType(String text) {
        if (text == null || text.isBlank()) return null;
        String normalized = normalizeCaptionText(text);
        if (TABLE_CAPTION_PATTERN.matcher(normalized).matches()) return CaptionType.TABLE;
        if (FIGURE_CAPTION_PATTERN.matcher(normalized).matches()) return CaptionType.FIGURE;
        if (normalized.startsWith("таблица ")) return CaptionType.TABLE;
        if (normalized.startsWith("рисунок ")) return CaptionType.FIGURE;
        return null;
    }

    private static String sanitizeText(String value) {
        if (value == null) return null;
        return value.trim();
    }

    private static String normalizeCaptionText(String text) {
        return text
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\u200E", "")
                .replace("\u200F", "")
                .replace('\uFEFF', ' ')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private enum CaptionType {
        TABLE,
        FIGURE
    }

    private static final class CaptionCandidate {
        private final CaptionType type;
        private final String text;
        private final int bodyIndex;
        private final int paragraphIndex;

        private CaptionCandidate(CaptionType type, String text, int bodyIndex, int paragraphIndex) {
            this.type = type;
            this.text = text;
            this.bodyIndex = bodyIndex;
            this.paragraphIndex = paragraphIndex;
        }
    }

    private static final class BodyParagraphMeta {
        private final int bodyIndex;
        private final int paragraphIndex;
        private final String text;
        private final boolean hasPicture;

        private BodyParagraphMeta(int bodyIndex, int paragraphIndex, String text, boolean hasPicture) {
            this.bodyIndex = bodyIndex;
            this.paragraphIndex = paragraphIndex;
            this.text = text;
            this.hasPicture = hasPicture;
        }
    }

    private static final class ParagraphStyleSnapshot {
        private String fontName;
        private Double fontSizePt;
        private Boolean bold;
        private Boolean italic;
        private String colorHex;
        private String alignment;
        private Double lineSpacing;
        private Double firstLineIndentCm;
        private Double leftIndentCm;
    }
}
