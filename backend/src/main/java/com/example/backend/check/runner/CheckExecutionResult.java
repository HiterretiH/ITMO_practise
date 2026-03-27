package com.example.backend.check.runner;

import java.util.List;

/**
 * Результат одной проверки по конфигурации: при {@code ran == false} правило отключено в {@code checks-config.json}.
 */
public record CheckExecutionResult(String id, String title, boolean ran, List<String> issues) {}
