package com.example.backend.util;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;

/**
 * Привязка абзацев к номерам страниц по OOXML: явные разрывы {@code w:br w:type="page"},
 * {@code w:lastRenderedPageBreak} (как Word сохранил вёрстку) и разрыв секции «следующая страница».
 * <p>
 * Без движка вёрстки Word это оценка; для проверок вроде ФТ-6 обычно достаточно.
 */
public final class PageLocator {

    private PageLocator() {
    }

    /**
     * @return номер страницы, с которой должен начинаться следующий абзац в потоке
     */
    public static int nextParagraphStartAfter(XWPFParagraph p, int paragraphStartPage) {
        int endPage = paragraphEndPage(p, paragraphStartPage);
        if (hasNextPageSectionBreak(p)) {
            return endPage + 1;
        }
        return endPage;
    }

    public static int paragraphEndPage(XWPFParagraph p, int paragraphStartPage) {
        int end = paragraphStartPage;
        for (XWPFRun run : p.getRuns()) {
            end += countPageAdvancesInRun(run);
        }
        return end;
    }

    public static int countPageAdvancesInRun(XWPFRun run) {
        CTR ctr = run.getCTR();
        int n = 0;
        for (CTBr br : ctr.getBrList()) {
            if (br.isSetType() && STBrType.PAGE.equals(br.getType())) {
                n++;
            }
        }
        if (ctr.sizeOfLastRenderedPageBreakArray() > 0) {
            n += ctr.sizeOfLastRenderedPageBreakArray();
        }
        return n;
    }

    public static boolean hasNextPageSectionBreak(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().getPPr();
        if (pPr == null || !pPr.isSetSectPr()) {
            return false;
        }
        CTSectPr sp = pPr.getSectPr();
        if (!sp.isSetType()) {
            return false;
        }
        if (sp.getType().getVal() == null) {
            return false;
        }
        return STSectionMark.NEXT_PAGE.equals(sp.getType().getVal());
    }

}
