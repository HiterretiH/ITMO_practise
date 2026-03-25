package com.example.backend.domain;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DocumentPageSettings {

    @Builder.Default
    private List<SectionPageInfo> sections = new ArrayList<>();

    private PageNumberingInfo numbering;
}
