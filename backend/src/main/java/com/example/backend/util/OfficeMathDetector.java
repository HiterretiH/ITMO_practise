package com.example.backend.util;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * Обнаружение формул Office Math (OMML) в абзаце DOCX.
 * <p>
 * <b>Где лежит в распакованном .docx:</b> основной поток — {@code word/document.xml}; при необходимости
 * те же узлы могут присутствовать в {@code word/header*.xml}, {@code word/footer*.xml}.
 * <p>
 * <b>Что искать в XML (ECMA-376, Office Math ML):</b>
 * <ul>
 *   <li>Пространство имён математики:
 *       {@value #OMML_NAMESPACE_URI}</li>
 *   <li>{@code m:oMath} — встроенная формула (внутри {@code w:r} или в составе абзаца);</li>
 *   <li>{@code m:oMathPara} — абзац целиком как математика (блочная формула).</li>
 * </ul>
 * Word («Вставка → Уравнение») и LibreOffice Writer («Вставка → Объект → Формула» / встроенный редактор)
 * при сохранении в .docx записывают OMML; обычный текст в {@code w:t} без этих узлов <em>не</em> является формулой.
 * <p>
 * Символы вроде ∑ или дроби, набранные как обычный Unicode в тексте, OMML не создают — такие случаи
 * этот детектор намеренно не помечает как формулы.
 */
public final class OfficeMathDetector {

    /** URI пространства имён Office Math (OMML) в DOCX. */
    public static final String OMML_NAMESPACE_URI =
            "http://schemas.openxmlformats.org/officeDocument/2006/math";

    private OfficeMathDetector() {
    }

    /**
     * @return {@code true}, если в абзаце есть OMML-разметка формулы (редактор формул Word/LibreOffice).
     */
    public static boolean paragraphContainsOfficeMath(XWPFParagraph paragraph) {
        if (paragraph == null || paragraph.getCTP() == null) {
            return false;
        }
        return paragraphXmlContainsOmml(paragraph.getCTP().xmlText());
    }

    /**
     * Разбор XML абзаца {@code w:p} (как {@link org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP#xmlText()}).
     * Для тестов и отладки без загрузки .docx.
     */
    public static boolean paragraphXmlContainsOmml(String ctpXml) {
        if (ctpXml == null || ctpXml.isEmpty()) {
            return false;
        }
        // Типовые префиксы: m:oMath, m:oMathPara (xmlns:m указывает на OMML_NAMESPACE_URI)
        if (ctpXml.contains("m:oMath") || ctpXml.contains("m:oMathPara")) {
            return true;
        }
        // Реже: без префикса m (другой префикс или иная сериализация)
        return ctpXml.contains("oMathPara") || ctpXml.contains(":oMath");
    }
}
