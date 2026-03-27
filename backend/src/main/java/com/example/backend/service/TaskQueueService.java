package com.example.backend.service;

import com.example.backend.exception.ValidationException;
import com.example.backend.json.ValidateJobStatusResponse;
import com.example.backend.json.ValidationResult;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class TaskQueueService {

    private final CheckEngineService checkEngineService;
    private final ThreadPoolExecutor executor;
    private final Map<UUID, JobEntry> jobs = new ConcurrentHashMap<>();
    private final Duration terminalJobTtl;

    public TaskQueueService(
            CheckEngineService checkEngineService,
            @Value("${vkr.validation.queue.core-pool-size:2}") int corePoolSize,
            @Value("${vkr.validation.queue.max-pool-size:4}") int maxPoolSize,
            @Value("${vkr.validation.queue.queue-capacity:32}") int queueCapacity,
            @Value("${vkr.validation.job-terminal-ttl-hours:1}") int terminalJobTtlHours) {
        this.checkEngineService = checkEngineService;
        this.terminalJobTtl = Duration.ofHours(Math.max(1, terminalJobTtlHours));
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "vkr-validate");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public UUID submit(Path tempFile, String filename, String contentType) {
        UUID id = UUID.randomUUID();
        JobEntry entry = new JobEntry();
        jobs.put(id, entry);
        try {
            executor.execute(() -> runJob(id, tempFile, filename, contentType));
        } catch (RejectedExecutionException e) {
            jobs.remove(id);
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
            }
            throw e;
        }
        return id;
    }

    private void runJob(UUID id, Path tempFile, String filename, String contentType) {
        try {
            try (InputStream in = Files.newInputStream(tempFile)) {
                ValidationResult result = checkEngineService.validate(filename, contentType, in);
                JobEntry entry = jobs.get(id);
                if (entry != null) {
                    entry.complete(result);
                }
            }
        } catch (ValidationException e) {
            failJob(id, e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage();
            failJob(id, msg != null && !msg.isBlank() ? msg : "Ошибка обработки документа");
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
            }
        }
    }

    private void failJob(UUID id, String message) {
        JobEntry entry = jobs.get(id);
        if (entry != null) {
            entry.fail(message);
        }
    }

    public Optional<ValidateJobStatusResponse> getJob(UUID id) {
        JobEntry entry = jobs.get(id);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(entry.toResponse());
    }

    @Scheduled(fixedDelayString = "${vkr.validation.job-cleanup-interval-ms:600000}")
    public void evictExpiredTerminalJobs() {
        Instant cutoff = Instant.now().minus(terminalJobTtl);
        jobs.entrySet().removeIf(e -> e.getValue().isTerminal() && e.getValue().createdAt().isBefore(cutoff));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class JobEntry {
        private final Instant createdAt = Instant.now();
        private volatile String status = "pending";
        private volatile ValidationResult result;
        private volatile String message;

        void complete(ValidationResult r) {
            this.result = r;
            this.status = "completed";
        }

        void fail(String message) {
            this.message = message;
            this.status = "failed";
        }

        boolean isTerminal() {
            return "completed".equals(status) || "failed".equals(status);
        }

        Instant createdAt() {
            return createdAt;
        }

        ValidateJobStatusResponse toResponse() {
            return ValidateJobStatusResponse.builder()
                    .status(status)
                    .result(result)
                    .message(message)
                    .build();
        }
    }
}
