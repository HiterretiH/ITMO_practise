package com.example.backend.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StyleDefinition {

    private String styleId;

    private String name;

    private String styleType;

    private String basedOnStyleId;

    private Integer outlineLevel;
}
