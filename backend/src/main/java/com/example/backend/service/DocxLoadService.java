package com.example.backend.service;

import com.example.backend.exception.ValidationException;
import com.example.backend.model.domain.*;
import com.example.backend.util.DocumentFileValidator;
import com.example.backend.util.PageLocator;
import org.apache.poi.ooxml.POIXMLTypeLoader;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.StylesDocument;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.xmlbeans.XmlObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    /** Поле номера страницы в OOXML ({@code w:instrText}). */
    private static final Pattern OOXML_PAGE_FIELD = Pattern.compile("(?i)<w:instrText[^>]*>\\s*PAGE\\s");
    private static final Pattern OOXML_NUMPAGES_FIELD = Pattern.compile("(?i)<w:instrText[^>]*>\\s*NUMPAGES\\s");
    /** Элементы шрифта в {@code w:rPr} — разные версии XmlBeans дают разный API, поэтому смотрим XML. */
    private static final Pattern OOXML_RPR_CAPS = Pattern.compile("<w:caps\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OOXML_RPR_SMALL_CAPS = Pattern.compile("<w:smallCaps\\b", Pattern.CASE_INSENSITIVE);
    /** Выравнивание по центру в {@code w:pPr} (для колонтитула с номером страницы). */
    private static final Pattern OOXML_JC_CENTER = Pattern.compile("<w:jc\\s[^>]*w:val=\"center\"", Pattern.CASE_INSENSITIVE);
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
            int nextParagraphStartPage = 1;
            Iterator<IBodyElement> it = doc.getBodyElementsIterator();
            while (it.hasNext()) {
                IBodyElement element = it.next();
                if (element instanceof XWPFParagraph xp) {
                    int startPage = nextParagraphStartPage;
                    ParagraphInfo info = mapParagraph(xp, startPage, false);
                    nextParagraphStartPage = PageLocator.nextParagraphStartAfter(xp, startPage);
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
                    int tablePage = nextParagraphStartPage;
                    tableInfos.add(TableInfo.builder()
                            .caption(tableCaption)
                            .paragraphIndex(paraIndex)
                            .pageIndex(tablePage)
                            .build());
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph p : cell.getParagraphs()) {
                                int startPage = nextParagraphStartPage;
                                ParagraphInfo pi = mapParagraph(p, startPage, true);
                                nextParagraphStartPage = PageLocator.nextParagraphStartAfter(p, startPage);
                                paragraphs.add(pi);
                                if (pi.getText() != null) fullText.append(pi.getText()).append(" ");
                                paraIndex++;
                            }
                        }
                    }
                } else if (element instanceof XWPFSDT sdt) {
                    int[] idx = new int[] {paraIndex, nextParagraphStartPage};
                    appendSdtContent(sdt, bodyIndex, paragraphs, bodyParagraphs, captions, fullText, tableInfos, idx);
                    paraIndex = idx[0];
                    nextParagraphStartPage = idx[1];
                }
                bodyIndex++;
            }

            populateFigureInfos(figureInfos, bodyParagraphs);
            syncFigurePageIndices(figureInfos, paragraphs);
            linkCaptionsToTables(captions, tableInfos, bodyParagraphs);
            linkCaptionsToFigures(captions, figureInfos, bodyParagraphs);

            PageMargins margins = extractMargins(doc);
            DocumentPageSettings pageSettings = extractDocumentPageSettings(doc);
            List<StyleDefinition> styleDefinitions = extractStyleDefinitions(doc);
            return DocumentStructure.builder()
                    .paragraphs(paragraphs)
                    .margins(margins)
                    .pageSettings(pageSettings)
                    .styleDefinitions(styleDefinitions)
                    .tables(tableInfos)
                    .figures(figureInfos)
                    .fullText(fullText.toString().trim())
                    .format("docx")
                    .build();
        } catch (IOException e) {
            throw new ValidationException("Не удалось прочитать документ .docx: " + e.getMessage());
        }
    }

    /**
     * Оглавление Word часто помещают в {@code w:sdt} (элемент управления, галерея Table of Contents).
     * Итератор тела документа отдаёт один {@link XWPFSDT}; без обхода вложенных абзацев строки оглавления
     * не попадают в список {@link ParagraphInfo}.
     * <p>
     * В POI 5.2.5 у {@link XWPFSDTContent} нет публичного {@code getBodyElements()} — читаем поле
     * {@code bodyElements}, которое заполняет тот же конструктор, что и при разборе XML.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> extractSdtBodyElements(XWPFSDT sdt) {
        ISDTContent content = sdt.getContent();
        if (!(content instanceof XWPFSDTContent sdtContent)) {
            return List.of();
        }
        try {
            Field f = XWPFSDTContent.class.getDeclaredField("bodyElements");
            f.setAccessible(true);
            Object raw = f.get(sdtContent);
            if (raw instanceof List<?>) {
                return (List<Object>) raw;
            }
        } catch (ReflectiveOperationException ignored) {
            // другая версия POI или обфускация — оглавление из SDT недоступно
        }
        return List.of();
    }

    private void appendSdtContent(
            XWPFSDT sdt,
            int bodyIndexForBlock,
            List<ParagraphInfo> paragraphs,
            List<BodyParagraphMeta> bodyParagraphs,
            List<CaptionCandidate> captions,
            StringBuilder fullText,
            List<TableInfo> tableInfos,
            int[] idx
    ) {
        List<Object> items = extractSdtBodyElements(sdt);
        if (items.isEmpty()) {
            return;
        }
        for (Object o : items) {
            if (o instanceof XWPFParagraph xp) {
                int startPage = idx[1];
                ParagraphInfo info = mapParagraph(xp, startPage, false);
                idx[1] = PageLocator.nextParagraphStartAfter(xp, startPage);
                paragraphs.add(info);
                String paragraphText = sanitizeText(info.getText());
                bodyParagraphs.add(new BodyParagraphMeta(bodyIndexForBlock, idx[0], paragraphText, hasPictures(xp)));
                CaptionType captionType = detectCaptionType(paragraphText);
                if (captionType != null) {
                    captions.add(new CaptionCandidate(captionType, paragraphText, bodyIndexForBlock, idx[0]));
                }
                if (info.getText() != null && !info.getText().isBlank()) {
                    fullText.append(info.getText()).append("\n");
                }
                idx[0]++;
            } else if (o instanceof XWPFTable table) {
                String tableCaption = findNearbyTableCaption(bodyParagraphs, captions, idx[0]);
                int tablePage = idx[1];
                tableInfos.add(TableInfo.builder()
                        .caption(tableCaption)
                        .paragraphIndex(idx[0])
                        .pageIndex(tablePage)
                        .build());
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            int startPage = idx[1];
                            ParagraphInfo pi = mapParagraph(p, startPage, true);
                            idx[1] = PageLocator.nextParagraphStartAfter(p, startPage);
                            paragraphs.add(pi);
                            if (pi.getText() != null) {
                                fullText.append(pi.getText()).append(" ");
                            }
                            idx[0]++;
                        }
                    }
                }
            } else if (o instanceof XWPFSDT nested) {
                appendSdtContent(nested, bodyIndexForBlock, paragraphs, bodyParagraphs, captions, fullText, tableInfos, idx);
            }
        }
    }

    private ParagraphInfo mapParagraph(XWPFParagraph xp, Integer pageIndex, boolean inTable) {
        String text = xp.getText();
        boolean hasFormula = paragraphContainsOfficeMath(xp);
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
        boolean anyCaps = false;
        boolean anySmallCaps = false;
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
            if (run.getCTR().getRPr() != null) {
                String rPrXml = run.getCTR().getRPr().xmlText();
                if (rPrXml != null) {
                    if (OOXML_RPR_CAPS.matcher(rPrXml).find()) {
                        anyCaps = true;
                    }
                    if (OOXML_RPR_SMALL_CAPS.matcher(rPrXml).find()) {
                        anySmallCaps = true;
                    }
                }
            }
        }
        if (runCount > 0) {
            style.bold = anyBold;
            style.italic = anyItalic;
            style.fontName = mostFrequent(fontCounts);
            style.fontSizePt = mostFrequent(sizeCounts);
            style.colorHex = mostFrequent(colorCounts);
            style.caps = anyCaps;
            style.smallCaps = anySmallCaps;
        }

        applyStyleFallbacks(xp, style);
        normalizeStyleValues(style, text);

        String paragraphStyleId = xp.getStyleID();
        String paragraphStyleName = null;
        if (paragraphStyleId != null && !paragraphStyleId.isBlank()
                && xp.getDocument() != null && xp.getDocument().getStyles() != null) {
            XWPFStyle st = xp.getDocument().getStyles().getStyle(paragraphStyleId);
            if (st != null) {
                paragraphStyleName = st.getName();
            }
        }
        Integer outlineLevel = resolveParagraphOutlineLevel(xp);

        Integer pageEnd = null;
        if (pageIndex != null) {
            pageEnd = PageLocator.paragraphEndPage(xp, pageIndex);
        }

        return ParagraphInfo.builder()
                .text(text)
                .styleId(paragraphStyleId)
                .styleName(paragraphStyleName)
                .outlineLevel(outlineLevel)
                .caps(style.caps)
                .smallCaps(style.smallCaps)
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
                .pageEndIndex(pageEnd)
                .inTable(inTable)
                .containsFormula(hasFormula)
                .build();
    }

    /** Формулы Word: Office Math ({@code m:oMath}). */
    private static boolean paragraphContainsOfficeMath(XWPFParagraph xp) {
        String xml = xp.getCTP().xmlText();
        return xml != null && (xml.contains("m:oMath") || xml.contains("oMathPara"));
    }

    private static void syncFigurePageIndices(List<FigureInfo> figures, List<ParagraphInfo> paragraphs) {
        for (FigureInfo f : figures) {
            int idx = f.getParagraphIndex();
            if (idx >= 0 && idx < paragraphs.size()) {
                ParagraphInfo pi = paragraphs.get(idx);
                if (pi.getPageIndex() != null) {
                    f.setPageIndex(pi.getPageIndex());
                }
            }
        }
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

    /**
     * Секции ({@code w:sectPr}), поля, размер листа, параметры {@code w:pgNumType}; нумерация в колонтитулах (ФТ-3, ФТ-12).
     */
    private DocumentPageSettings extractDocumentPageSettings(XWPFDocument doc) {
        List<CTSectPr> sectPrs = new ArrayList<>();
        for (XWPFParagraph p : doc.getParagraphs()) {
            if (p.getCTP().getPPr() != null && p.getCTP().getPPr().isSetSectPr()) {
                sectPrs.add(p.getCTP().getPPr().getSectPr());
            }
        }
        if (doc.getDocument().getBody().isSetSectPr()) {
            sectPrs.add(doc.getDocument().getBody().getSectPr());
        }

        PageNumberingInfo numbering = analyzePageNumbering(doc);
        if (sectPrs.isEmpty()) {
            if (numbering != null) {
                numbering.setPageNumberRestartInSections(false);
            }
            return DocumentPageSettings.builder()
                    .sections(List.of())
                    .numbering(numbering)
                    .build();
        }

        List<SectionPageInfo> sections = new ArrayList<>();
        for (int i = 0; i < sectPrs.size(); i++) {
            sections.add(mapSectPr(sectPrs.get(i), i));
        }
        boolean restart = sections.stream()
                .anyMatch(s -> Boolean.TRUE.equals(s.getSectionRestartsPageNumbering()));
        if (numbering != null) {
            numbering.setPageNumberRestartInSections(restart);
        }
        return DocumentPageSettings.builder()
                .sections(sections)
                .numbering(numbering)
                .build();
    }

    private static SectionPageInfo mapSectPr(CTSectPr sectPr, int index) {
        var b = SectionPageInfo.builder().sectionIndex(index);
        if (sectPr.isSetPgMar()) {
            CTPageMar pgMar = sectPr.getPgMar();
            b.margins(PageMargins.builder()
                    .leftCm(twipsToCm(pgMar.getLeft()))
                    .rightCm(twipsToCm(pgMar.getRight()))
                    .topCm(twipsToCm(pgMar.getTop()))
                    .bottomCm(twipsToCm(pgMar.getBottom()))
                    .build());
        }
        if (sectPr.isSetPgSz()) {
            CTPageSz pgSz = sectPr.getPgSz();
            if (pgSz.getW() != null) {
                b.pageWidthTwips(toLong(pgSz.getW()));
            }
            if (pgSz.getH() != null) {
                b.pageHeightTwips(toLong(pgSz.getH()));
            }
            if (pgSz.isSetOrient()) {
                String o = pgSz.getOrient().toString();
                b.landscape(o != null && o.toLowerCase(Locale.ROOT).contains("landscape"));
            }
        }
        if (sectPr.isSetPgNumType()) {
            CTPageNumber pn = sectPr.getPgNumType();
            // Только явный w:start означает заданный старт нумерации; сам по себе w:pgNumType с fmt=decimal
            // на каждом разрыве секции в Word — не «перезапуск».
            b.sectionRestartsPageNumbering(pn.isSetStart());
            if (pn.isSetStart()) {
                b.pageNumberStart(pn.getStart().intValue());
            }
            if (pn.isSetFmt() && pn.getFmt() != null) {
                b.pageNumberFormat(pn.getFmt().toString());
            }
        } else {
            b.sectionRestartsPageNumbering(false);
        }
        return b.build();
    }

    private PageNumberingInfo analyzePageNumbering(XWPFDocument doc) {
        List<XWPFFooter> footers = collectUniqueFooters(doc);
        List<XWPFHeader> headers = collectUniqueHeaders(doc);

        boolean footerPage = false;
        boolean headerPage = false;
        Boolean footerCenter = null;

        for (XWPFFooter f : footers) {
            String rawXml = readPartXml(f);
            if (footerXmlContainsPageField(rawXml)) {
                footerPage = true;
                boolean centered = footerXmlHasCenterAlignment(rawXml);
                footerCenter = footerCenter == null ? centered : footerCenter && centered;
                continue;
            }
            // Запасной путь: абзацы без SDT (классическое поле PAGE).
            for (XWPFParagraph p : collectParagraphsDeep(f)) {
                if (!paragraphContainsPageNumberField(p)) {
                    continue;
                }
                footerPage = true;
                boolean centered = p.getAlignment() == ParagraphAlignment.CENTER;
                footerCenter = footerCenter == null ? centered : footerCenter && centered;
            }
        }
        for (XWPFHeader h : headers) {
            String rawXml = readPartXml(h);
            if (headerXmlContainsPageField(rawXml)) {
                headerPage = true;
                continue;
            }
            for (XWPFParagraph p : collectParagraphsDeep(h)) {
                if (paragraphContainsPageNumberField(p)) {
                    headerPage = true;
                }
            }
        }

        List<String> notes = new ArrayList<>();
        if (footerPage && Boolean.FALSE.equals(footerCenter)) {
            notes.add("Номер страницы в подвале: абзац с полем PAGE не выровнен по центру (ожидается по центру внизу по ФТ-12).");
        }
        if (!footerPage && headerPage) {
            notes.add("Поле номера страницы найдено в верхнем колонтитуле; по ФТ-12 номер должен быть внизу по центру.");
        }

        return PageNumberingInfo.builder()
                .footerPageFieldPresent(footerPage)
                .headerPageFieldPresent(headerPage)
                .footerPartCount(footers.size())
                .headerPartCount(headers.size())
                .footerPageParagraphCentered(footerPage ? footerCenter : null)
                .pageNumberRestartInSections(false)
                .footerNotes(notes)
                .build();
    }

    private static List<XWPFFooter> collectUniqueFooters(XWPFDocument doc) {
        LinkedHashSet<XWPFFooter> set = new LinkedHashSet<>();
        List<XWPFFooter> list = doc.getFooterList();
        if (list != null) {
            set.addAll(list);
        }
        XWPFHeaderFooterPolicy policy = doc.getHeaderFooterPolicy();
        if (policy != null) {
            addIfNotNull(set, policy.getDefaultFooter());
            addIfNotNull(set, policy.getFirstPageFooter());
            addIfNotNull(set, policy.getEvenPageFooter());
        }
        return new ArrayList<>(set);
    }

    private static List<XWPFHeader> collectUniqueHeaders(XWPFDocument doc) {
        LinkedHashSet<XWPFHeader> set = new LinkedHashSet<>();
        List<XWPFHeader> list = doc.getHeaderList();
        if (list != null) {
            set.addAll(list);
        }
        XWPFHeaderFooterPolicy policy = doc.getHeaderFooterPolicy();
        if (policy != null) {
            addIfNotNull(set, policy.getDefaultHeader());
            addIfNotNull(set, policy.getFirstPageHeader());
            addIfNotNull(set, policy.getEvenPageHeader());
        }
        return new ArrayList<>(set);
    }

    private static <T> void addIfNotNull(LinkedHashSet<T> set, T part) {
        if (part != null) {
            set.add(part);
        }
    }

    private static List<XWPFParagraph> collectParagraphsDeep(XWPFHeaderFooter part) {
        List<XWPFParagraph> out = new ArrayList<>();
        for (IBodyElement el : part.getBodyElements()) {
            if (el instanceof XWPFParagraph p) {
                out.add(p);
            } else if (el instanceof XWPFTable table) {
                collectParagraphsFromTable(table, out);
            }
        }
        return out;
    }

    private static void collectParagraphsFromTable(XWPFTable table, List<XWPFParagraph> out) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    out.add(p);
                }
                for (XWPFTable nested : cell.getTables()) {
                    collectParagraphsFromTable(nested, out);
                }
            }
        }
    }

    /**
     * Поле номера страницы: {@code PAGE}, иногда {@code NUMPAGES}; также {@code fldSimple} с instr PAGE.
     */
    private static boolean paragraphContainsPageNumberField(XWPFParagraph p) {
        String xml = p.getCTP().xmlText();
        if (xml == null || xml.isEmpty()) {
            return false;
        }
        if (OOXML_PAGE_FIELD.matcher(xml).find() || OOXML_NUMPAGES_FIELD.matcher(xml).find()) {
            return true;
        }
        if (xml.contains("w:fldSimple") && xml.contains("PAGE")) {
            return true;
        }
        return xml.contains("instrText") && Pattern.compile("(?i)instrText[^>]*>[^<]*\\bPAGE\\b").matcher(xml).find();
    }

    private static String readPartXml(XWPFHeaderFooter part) {
        try (InputStream is = part.getPackagePart().getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Полный XML колонтитула: классическое поле PAGE, {@code fldSimple}, галерея Word «Page Numbers (Bottom of Page)» в {@code w:sdt}.
     */
    private static boolean footerXmlContainsPageField(String xml) {
        if (xml == null || xml.isEmpty()) {
            return false;
        }
        if (OOXML_PAGE_FIELD.matcher(xml).find() || OOXML_NUMPAGES_FIELD.matcher(xml).find()) {
            return true;
        }
        if (xml.contains("w:fldSimple") && Pattern.compile("(?i)instr\\s*=\\s*[\"'][^\"']*PAGE").matcher(xml).find()) {
            return true;
        }
        if (xml.contains("docPartGallery") && xml.toLowerCase(Locale.ROOT).contains("page numbers")) {
            return true;
        }
        return false;
    }

    private static boolean headerXmlContainsPageField(String xml) {
        return footerXmlContainsPageField(xml);
    }

    private static boolean footerXmlHasCenterAlignment(String xml) {
        return OOXML_JC_CENTER.matcher(xml).find();
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

    private static Long toLong(Object value) {
        BigInteger bi = toBigInteger(value);
        return bi == null ? null : bi.longValue();
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

    /**
     * Доступ к {@link CTStyles}: в POI 5.2.x — {@code getCtStyles()}, в новых — {@code getCTStyles()}.
     */
    private static XmlObject resolveCtStyles(XWPFStyles styles) {
        if (styles == null) {
            return null;
        }
        for (String methodName : new String[] {"getCtStyles", "getCTStyles"}) {
            try {
                var method = styles.getClass().getMethod(methodName);
                Object value = method.invoke(styles);
                if (value instanceof XmlObject xml) {
                    return xml;
                }
            } catch (ReflectiveOperationException ignored) {
                // try next
            }
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
        private Boolean caps;
        private Boolean smallCaps;
    }

    /**
     * Каталог стилей из styles.xml (ФТ-3: параметры стилей; ФТ-11: единообразие заголовков).
     */
    private static List<StyleDefinition> extractStyleDefinitions(XWPFDocument doc) {
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return List.of();
        }
        CTStyles ctStyles = resolveCtStylesRoot(styles, doc);
        if (ctStyles == null) {
            return List.of();
        }
        List<StyleDefinition> out = new ArrayList<>();
        for (CTStyle ct : ctStyles.getStyleArray()) {
            if (ct == null) {
                continue;
            }
            String id = ct.getStyleId();
            if (id == null || id.isBlank()) {
                continue;
            }
            String name = ct.isSetName() && ct.getName() != null ? ct.getName().getVal() : null;
            String type = null;
            if (ct.isSetType()) {
                type = ct.getType().toString();
            }
            String basedOn = extractBasedOnStyleId(ct.xmlText());
            Integer outline = null;
            if (ct.isSetPPr() && ct.getPPr().isSetOutlineLvl()) {
                outline = ct.getPPr().getOutlineLvl().getVal().intValue();
            }
            out.add(StyleDefinition.builder()
                    .styleId(id)
                    .name(name)
                    .styleType(type)
                    .basedOnStyleId(basedOn)
                    .outlineLevel(outline)
                    .build());
        }
        return out;
    }

    /**
     * Корень {@code w:styles}: из {@link XWPFStyles}, повторный разбор XML или чтение {@code /word/styles.xml}.
     */
    private static CTStyles resolveCtStylesRoot(XWPFStyles styles, XWPFDocument doc) {
        XmlObject xo = resolveCtStyles(styles);
        if (xo instanceof CTStyles cs) {
            return cs;
        }
        // Иногда XmlBeans отдаёт тип, с которым не срабатывает instanceof CTStyles — читаем part напрямую.
        return loadCtStylesFromPackage(doc);
    }

    private static CTStyles loadCtStylesFromPackage(XWPFDocument doc) {
        try {
            OPCPackage pkg = doc.getPackage();
            PackagePart part = pkg.getPart(PackagingURIHelper.createPartName("/word/styles.xml"));
            try (InputStream is = part.getInputStream()) {
                StylesDocument sd = StylesDocument.Factory.parse(is, POIXMLTypeLoader.DEFAULT_XML_OPTIONS);
                return sd.getStyles();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Уровень структуры абзаца: сначала {@code w:outlineLvl} в абзаце, иначе цепочка {@code w:basedOn}.
     */
    private static Integer resolveParagraphOutlineLevel(XWPFParagraph xp) {
        if (xp.getCTP().getPPr() != null && xp.getCTP().getPPr().isSetOutlineLvl()) {
            return xp.getCTP().getPPr().getOutlineLvl().getVal().intValue();
        }
        String sid = xp.getStyleID();
        if (sid == null || sid.isBlank() || xp.getDocument() == null || xp.getDocument().getStyles() == null) {
            return null;
        }
        return resolveOutlineLevelFromStyleChain(xp.getDocument().getStyles(), sid, new HashSet<>());
    }

    private static Integer resolveOutlineLevelFromStyleChain(XWPFStyles styles, String styleId, Set<String> visited) {
        if (styleId == null || visited.contains(styleId)) {
            return null;
        }
        visited.add(styleId);
        XWPFStyle style = styles.getStyle(styleId);
        if (style == null || style.getCTStyle() == null) {
            return null;
        }
        CTStyle ct = style.getCTStyle();
        if (ct.isSetPPr() && ct.getPPr().isSetOutlineLvl()) {
            return ct.getPPr().getOutlineLvl().getVal().intValue();
        }
        String basedOn = extractBasedOnStyleId(ct.xmlText());
        if (basedOn != null) {
            return resolveOutlineLevelFromStyleChain(styles, basedOn, visited);
        }
        return null;
    }
}
