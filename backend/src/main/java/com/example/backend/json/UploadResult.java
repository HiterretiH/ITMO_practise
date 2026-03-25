package com.example.backend.json;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {
    private boolean parsed;
    private String format;
    private int paragraphs;
    private int tables;
    private int figures;
}
