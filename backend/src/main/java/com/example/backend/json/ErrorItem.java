package com.example.backend.json;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ErrorItem {
    private UUID id;
    private ErrorType type;
    private ErrorSeverity severity;
    private ErrorLocation location;
    private String description;
    private String expected;
    private String actual;
    private String recommendation;
}
