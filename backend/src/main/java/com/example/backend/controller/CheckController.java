package com.example.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/check")
public class CheckController {

    @PostMapping("/upload")
    public ResponseEntity<Void> uploadDocument() {
        // Заглушка эндпоинта загрузки файла для будущей реализации
        return ResponseEntity.accepted().build();
    }
}

