package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Описание стиля из word/styles.xml (для проверок единообразия ФТ-11 и сопоставления с outline).
 */
@Data
@Builder
public class StyleDefinition {

    /** Внутренний идентификатор Word ({@code w:styleId}). */
    private String styleId;

    /** Отображаемое имя ({@code w:name}). */
    private String name;

    /**
     * Тип стиля OOXML: paragraph, character, table и т.д.
     */
    private String styleType;

    /** Родительский стиль ({@code w:basedOn}). */
    private String basedOnStyleId;

    /**
     * Уровень структуры ({@code w:outlineLvl}) для заголовков; null — не задан в определении стиля.
     */
    private Integer outlineLevel;
}
