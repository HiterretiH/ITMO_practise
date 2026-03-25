package com.example.backend.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FigureInfo {
    private String caption;
    private int paragraphIndex;
    private int pageIndex;

    /**
     * Индекс абзаца с подписью к рисунку (если найден при разборе); для ФТ-13 (позиция и выравнивание).
     */
    private Integer captionParagraphIndex;
}
