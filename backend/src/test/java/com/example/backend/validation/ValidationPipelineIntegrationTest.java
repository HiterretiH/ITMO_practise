package com.example.backend.validation;

import com.example.backend.check.runner.CheckExecutionResult;
import com.example.backend.check.runner.VkrChecksRunner;
import com.example.backend.config.checks.ChecksConfigurationLoader;
import com.example.backend.config.checks.ChecksConfigRoot;
import com.example.backend.domain.DocumentStructure;
import com.example.backend.service.DocxLoadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * <p><b>Функциональный / интеграционный уровень</b> (по отношению к юнит-тестам отдельных ФТ):
 * один реальный .docx проходит загрузку и полный прогон {@link VkrChecksRunner} с конфигом из classpath.
 * Это проверяет «склейку» модулей, а не изолированную логику одного чекера.</p>
 *
 * <p>Рекомендуемая связка с заданием про «юнит-тестирование ключевых модулей»:</p>
 * <ul>
 *   <li><b>Юнит-тесты</b> — уже есть по отдельным ФТ (конструируемые {@code ParagraphInfo}, граничные случаи).</li>
 *   <li><b>Интеграционные тесты</b> (этот класс) — реальный файл + полный конвейер; дополняют юниты, а не заменяют.</li>
 *   <li><b>Точность / ложные срабатывания</b> — фиксируются эталонными документами и ожидаемыми наборами замечаний
 *       (golden/regression), по мере готовности.</li>
 * </ul>
 */
class ValidationPipelineIntegrationTest {

    private static final Path SAMPLE_DOCX = Path.of("test-input", "test.docx");

    static boolean sampleDocxExists() {
        return Files.isRegularFile(SAMPLE_DOCX);
    }

    @Test
    @EnabledIf("sampleDocxExists")
    @DisplayName("Полный прогон проверок по образцу test-input/test.docx завершается без исключений")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void fullPipelineCompletesOnSampleDocx() throws Exception {
        DocxLoadService loader = new DocxLoadService();
        ChecksConfigRoot cfg = ChecksConfigurationLoader.loadClasspath("checks-config.json");
        try (InputStream in = Files.newInputStream(SAMPLE_DOCX)) {
            DocumentStructure structure =
                    loader.load(
                            SAMPLE_DOCX.getFileName().toString(),
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            in);
            assertNotNull(structure.getParagraphs());

            List<CheckExecutionResult> results = VkrChecksRunner.run(structure, cfg);
            assertFalse(results.isEmpty(), "Должен быть хотя бы один результат по правилам из checks-config.json");
        }
    }
}
