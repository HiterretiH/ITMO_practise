package com.example.backend.controller;

import com.example.backend.json.ValidationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1")
public class ValidateController {

    @PostMapping(
            value = "/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ValidationResult> validateDocument(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(null);
    }
}
