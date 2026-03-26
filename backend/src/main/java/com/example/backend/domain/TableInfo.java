package com.example.backend.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableInfo {
    private String caption;
    /** Индекс первого абзаца внутри таблицы (первая ячейка). */
    private int paragraphIndex;

    /**
     * Индекс первого абзаца <strong>после</strong> таблицы (не входит в таблицу) — для пересечения раздела с таблицей (ФТ-17).
     */
    private int paragraphIndexEndExclusive;

    private int pageIndex;

    /** Число столбцов в первой строке (для ФТ-17 / ФТ-18). */
    private Integer columnCount;

    /** Число строк (для отладки и проверок). */
    private Integer rowCount;

    /** Абзац с названием таблицы над ней (если найден при разборе); для ФТ-14. */
    private Integer captionParagraphIndex;
}
