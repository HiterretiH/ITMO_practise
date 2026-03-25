package com.example.backend.controller;

import com.example.backend.json.ReportGenerationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ReportController {

    @PostMapping(
            value = "/report",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> generateReport(@RequestBody ReportGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(null);
    }
}
