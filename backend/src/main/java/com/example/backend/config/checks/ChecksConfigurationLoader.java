package com.example.backend.config.checks;

import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Загрузка {@code checks-config.json} из classpath или из пути Spring {@link Resource}.
 */
@Component
public class ChecksConfigurationLoader {

    private static final Logger log = LoggerFactory.getLogger(ChecksConfigurationLoader.class);

    private static final Set<String> KNOWN_RULE_IDS = Set.of(
            "ft4",
            "ft5",
            "ft6",
            "ft7",
            "ft8",
            "ft9",
            "ft10",
            "ft11",
            "ft12",
            "ft13",
            "ft14",
            "ft15",
            "ft16",
            "ft17",
            "ft18",
            "ft19",
            "ft20",
            "ft21");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ResourceLoader resourceLoader;

    @Value("${vkr.checks.resource:classpath:checks-config.json}")
    private String resourceLocation;

    /** Кэш: конфиг не меняется в рантайме; повторный парс JSON на каждую валидацию убран. */
    private volatile ChecksConfigRoot cachedConfig;

    public ChecksConfigurationLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void warmCache() {
        long t0 = System.nanoTime();
        cachedConfig = loadFresh();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        int rules = cachedConfig.rules() != null ? cachedConfig.rules().size() : 0;
        log.info("checks-config loaded once from {} ({} rules, {} ms)", resourceLocation, rules, ms);
    }

    /** Для {@link com.example.backend.DocxLoadDebugMain} и тестов без Spring. */
    public static ChecksConfigRoot loadClasspath(String classpathLocation) {
        try (InputStream in =
                ChecksConfigurationLoader.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("Не найден ресурс classpath: " + classpathLocation);
            }
            ChecksConfigRoot root = MAPPER.readValue(in, ChecksConfigRoot.class);
            validate(root);
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Конфигурация проверок (кэшируется после старта приложения).
     */
    public ChecksConfigRoot load() {
        ChecksConfigRoot c = cachedConfig;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (cachedConfig == null) {
                cachedConfig = loadFresh();
            }
            return cachedConfig;
        }
    }

    private ChecksConfigRoot loadFresh() {
        Resource resource = resourceLoader.getResource(resourceLocation);
        try (InputStream in = resource.getInputStream()) {
            ChecksConfigRoot root = MAPPER.readValue(in, ChecksConfigRoot.class);
            validate(root);
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать " + resourceLocation, e);
        }
    }

    static void validate(ChecksConfigRoot root) {
        if (root == null) {
            throw new IllegalStateException("checks-config: пустой документ");
        }
        if (root.schemaVersion() != 1) {
            throw new IllegalStateException("checks-config: неподдерживаемая schemaVersion: " + root.schemaVersion());
        }
        List<CheckRuleDefinition> rules = root.rules();
        if (rules == null || rules.isEmpty()) {
            throw new IllegalStateException("checks-config: список rules пуст");
        }
        Set<String> seen = new HashSet<>();
        for (CheckRuleDefinition r : rules) {
            if (r.id() == null || r.id().isBlank()) {
                throw new IllegalStateException("checks-config: у правила пустой id");
            }
            String id = r.id().trim().toLowerCase(Locale.ROOT);
            if (!KNOWN_RULE_IDS.contains(id)) {
                throw new IllegalStateException("checks-config: неизвестный id правила (ожидаются ft4–ft21): " + r.id());
            }
            if (!seen.add(id)) {
                throw new IllegalStateException("checks-config: дублируется id: " + r.id());
            }
        }
    }
}
