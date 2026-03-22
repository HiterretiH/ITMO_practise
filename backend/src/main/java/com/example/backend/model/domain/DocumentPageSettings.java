package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Параметры страницы и нумерации, извлечённые из .docx (ФТ-3: поля страницы, информация о нумерации страниц).
 */
@Data
@Builder
public class DocumentPageSettings {

    @Builder.Default
    private List<SectionPageInfo> sections = new ArrayList<>();

    private PageNumberingInfo numbering;
}
