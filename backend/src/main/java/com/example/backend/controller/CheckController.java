package com.example.backend.controller;

import com.example.backend.model.domain.DocumentStructure;
import com.example.backend.model.dto.UploadResultDto;
import com.example.backend.service.DocxLoadService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/check")
public class CheckController {

    private final DocxLoadService docxLoadService;

    public CheckController(DocxLoadService docxLoadService) {
        this.docxLoadService = docxLoadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResultDto> uploadDocument(
            @RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        try (InputStream inputStream = file.getInputStream()) {
            DocumentStructure structure = docxLoadService.load(filename, contentType, inputStream);
            UploadResultDto dto = UploadResultDto.builder()
                .parsed(true)
                .format(structure.getFormat())
                .paragraphs(structure.getParagraphs().size())
                .tables(structure.getTables().size())
                .figures(structure.getFigures().size())
                .build();
            return ResponseEntity.ok(dto);
        }
    }
}
