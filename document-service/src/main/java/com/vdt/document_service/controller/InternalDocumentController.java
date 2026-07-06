package com.vdt.document_service.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.document_service.dto.DocumentResponse;
import com.vdt.document_service.dto.ExpiringDocumentDto;
import com.vdt.document_service.dto.StatusUpdateRequest;
import com.vdt.document_service.service.DocumentService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/internal/documents")
public class InternalDocumentController {
    
    private final DocumentService service;

    public InternalDocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/expiring")
    public List<ExpiringDocumentDto> expiring(@RequestParam(name="withinDays", defaultValue="30") int withinDays) {
        return service.findExpiring(withinDays);
    }
    
    @PatchMapping("/{id}/status")
    public DocumentResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest body) {
        return service.updateStatus(id, body.status());
    }

    /** Chạy tay job kích hoạt hiệu lực (demo/test, không chờ cron 0h05). */
    @PostMapping("/effective-check")
    public Map<String, Integer> effectiveCheck() {
        return Map.of("activated", service.activateDueDocuments());
    }
}
