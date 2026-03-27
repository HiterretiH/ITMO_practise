package com.example.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficeMathDetectorTest {

    /** Фрагмент из реального test2.docx: вставленная формула (Cambria Math, OMML). */
    private static final String SNIPPET_WITH_OMATH =
            "<w:pPr>...</w:pPr><w:r></w:r><m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">"
                    + "<m:r><m:t>E</m:t></m:r><m:r><m:t>=</m:t></m:r></m:oMath>";

    @Test
    void detectsMOmath() {
        assertTrue(OfficeMathDetector.paragraphXmlContainsOmml(SNIPPET_WITH_OMATH));
    }

    @Test
    void plainParagraphNotFormula() {
        assertFalse(
                OfficeMathDetector.paragraphXmlContainsOmml(
                        "<w:p><w:r><w:t>1 Контекст</w:t></w:r></w:p>"));
    }

    @Test
    void ommlNamespaceDocumented() {
        assertTrue(OfficeMathDetector.OMML_NAMESPACE_URI.contains("officeDocument/2006/math"));
    }
}
