package com.example.backend.validation;

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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Дымовая проверка производительности на доступном образце (не заменяет нагрузочное тестирование).
 * Для требования «до 100 стр.» при появлении эталонного крупного файла можно задать отдельный лимит и {@code assumeTrue}.
 */
class ValidationPerformanceSmokeTest {

    private static final Path SAMPLE_DOCX = Path.of("test-input", "test.docx");

    static boolean sampleDocxExists() {
        return Files.isRegularFile(SAMPLE_DOCX);
    }

    @Test
    @EnabledIf("sampleDocxExists")
    @DisplayName("Загрузка + полный прогон ФТ укладывается в разумный интервал (дымовой тест)")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void samplePipelineFinishesWithinSmokeBudget() throws Exception {
        DocxLoadService loader = new DocxLoadService();
        ChecksConfigRoot cfg = ChecksConfigurationLoader.loadClasspath("checks-config.json");
        long t0 = System.nanoTime();
        try (InputStream in = Files.newInputStream(SAMPLE_DOCX)) {
            DocumentStructure structure =
                    loader.load(
                            SAMPLE_DOCX.getFileName().toString(),
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            in);
            VkrChecksRunner.run(structure, cfg);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(ms < 180_000, "Обработка образца не должна занимать более 3 мин (дымовой порог): " + ms + " ms");
    }
}
