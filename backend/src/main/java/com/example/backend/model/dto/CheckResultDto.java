package com.example.backend.model.dto;

import java.util.List;

public class CheckResultDto {
    // Краткая статистика и список найденных ошибок
    private int totalErrors;
    private int criticalErrors;
    private List<ErrorItemDto> items;
}

