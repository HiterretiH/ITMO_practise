package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Данные для проверки ФТ-12 (нумерация страниц): наличие поля номера, положение в колонтитуле.
 */
@Data
@Builder
public class PageNumberingInfo {

    /** Обнаружено поле номера страницы (PAGE / NUMPAGES и т.п.) в нижнем колонтитуле. */
    private boolean footerPageFieldPresent;

    /**
     * Обнаружено поле номера страницы в верхнем колонтитуле (для отчёта; по ТЗ номер — внизу).
     */
    private boolean headerPageFieldPresent;

    /** Число уникальных нижних колонтитулов, в которых искались поля. */
    private int footerPartCount;

    /** Число уникальных верхних колонтитулов. */
    private int headerPartCount;

    /**
     * Если true — абзацы с полем PAGE в нижнем колонтитуле выровнены по центру (для ФТ-12: центр внизу).
     * null — если полей PAGE в подвале не найдено.
     */
    private Boolean footerPageParagraphCentered;

    /**
     * Встречается ли в документе перезапуск нумерации по секциям ({@code w:pgNumType}).
     */
    private boolean pageNumberRestartInSections;

    /**
     * В XML нижнего колонтитула после поля PAGE найден отдельный прогон с точкой (типичная ошибка «номер.»).
     */
    private boolean footerTrailingPeriodAfterPageSuspected;

    /** Поле PAGE в подвале по умолчанию (через HeaderFooterPolicy). */
    private boolean defaultFooterHasPageField;

    /** Задан отдельный подвал первой страницы. */
    private boolean firstPageFooterPresent;

    /** В отдельном подвале первой страницы есть поле PAGE (если {@link #firstPageFooterPresent}). */
    private boolean firstPageFooterHasPageField;

    /** Задан отдельный подвал чётных страниц. */
    private boolean evenPageFooterPresent;

    /** В подвале чётных страниц есть поле PAGE (если {@link #evenPageFooterPresent}). */
    private boolean evenPageFooterHasPageField;

    @Builder.Default
    private List<String> footerNotes = new ArrayList<>();
}
