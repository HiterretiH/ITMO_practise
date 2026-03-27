package com.example.backend.controller;

import com.example.backend.check.runner.CheckExecutionResult;
import com.example.backend.service.CheckEngineService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/v1")
public class ValidateController {

    private final CheckEngineService checkEngineService;

    public ValidateController(CheckEngineService checkEngineService) {
        this.checkEngineService = checkEngineService;
    }

    @PostMapping(
            value = "/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<CheckExecutionResult>> validateDocument(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(checkEngineService.validateDocument(file));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
