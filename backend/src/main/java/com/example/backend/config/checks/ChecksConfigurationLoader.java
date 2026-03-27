package com.example.backend.config.checks;

import tools.jackson.databind.json.JsonMapper;
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

    public ChecksConfigurationLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
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

    public ChecksConfigRoot load() {
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
