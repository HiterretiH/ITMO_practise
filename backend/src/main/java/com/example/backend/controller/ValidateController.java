package com.example.backend.controller;

import com.example.backend.json.ErrorResponse;
import com.example.backend.json.ValidateJobCreatedResponse;
import com.example.backend.json.ValidateJobStatusResponse;
import com.example.backend.service.TaskQueueService;
import com.example.backend.util.DocumentFileValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/v1")
public class ValidateController {

    private final TaskQueueService taskQueueService;

    public ValidateController(TaskQueueService taskQueueService) {
        this.taskQueueService = taskQueueService;
    }

    @PostMapping(
            value = "/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> validateDocument(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "document.docx";
        }
        DocumentFileValidator.validate(filename, file.getContentType());

        String ext = DocumentFileValidator.getExtension(filename);
        String suffix = ext.isEmpty() ? ".docx" : "." + ext;
        Path temp = Files.createTempFile("vkr-", suffix);
        boolean submitted = false;
        try {
            file.transferTo(temp);
            UUID jobId = taskQueueService.submit(temp, filename, file.getContentType());
            submitted = true;
            temp = null;
            ValidateJobCreatedResponse body = ValidateJobCreatedResponse.builder()
                    .jobId(jobId.toString())
                    .build();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        } catch (RejectedExecutionException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse.builder()
                            .code("QUEUE_FULL")
                            .message("Сервер перегружен. Повторите попытку позже.")
                            .build());
        } finally {
            if (!submitted && temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @GetMapping(value = "/validate/jobs/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidateJobStatusResponse> getValidationJob(@PathVariable UUID jobId) {
        return taskQueueService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}