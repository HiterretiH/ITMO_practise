package com.example.backend.config.checks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Корневой объект {@code checks-config.json}: порядок элементов в {@link #rules()} — порядок запуска проверок.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChecksConfigRoot(int schemaVersion, List<CheckRuleDefinition> rules) {}
