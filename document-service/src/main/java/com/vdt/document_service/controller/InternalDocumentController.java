package com.vdt.document_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.document_service.dto.ExpiringDocumentDto;
import com.vdt.document_service.service.DocumentService;


@RestController
@RequestMapping("/internal/documents/")
public class InternalDocumentController {
    
    private final DocumentService service;

    public InternalDocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/expiring")
    public List<ExpiringDocumentDto> expiring(@RequestParam(name="withinDays", defaultValue="30") int withinDays) {
        return service.findExpiring(withinDays);
    }
    
}
