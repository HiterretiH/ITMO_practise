package com.example.backend.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageMargins {
    private double leftCm;
    private double rightCm;
    private double topCm;
    private double bottomCm;
}
