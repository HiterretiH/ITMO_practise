package com.example.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResultDto {
    private boolean parsed;
    private String format;
    private int paragraphs;
    private int tables;
    private int figures;
}
