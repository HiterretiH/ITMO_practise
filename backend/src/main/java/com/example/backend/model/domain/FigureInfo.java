package com.example.backend.model.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FigureInfo {
    private String caption;
    private int paragraphIndex;
    private int pageIndex;
}
