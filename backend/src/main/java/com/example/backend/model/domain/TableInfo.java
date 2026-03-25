package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableInfo {
    private String caption;
    /** Индекс первого абзаца внутри таблицы (первая ячейка). */
    private int paragraphIndex;
    private int pageIndex;

    /** Абзац с названием таблицы над ней (если найден при разборе); для ФТ-14. */
    private Integer captionParagraphIndex;
}
