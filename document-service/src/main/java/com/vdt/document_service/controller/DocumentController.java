package com.vdt.document_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vdt.document_service.dto.DashboardStatsDto;
import com.vdt.document_service.dto.DocumentRequest;
import com.vdt.document_service.dto.DocumentResponse;
import com.vdt.document_service.dto.RejectRequest;
import com.vdt.document_service.dto.RenewRequest;
import com.vdt.document_service.service.DocumentService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @GetMapping
    public List<DocumentResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@Valid @RequestBody DocumentRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody DocumentRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/submit")
    public DocumentResponse submit(@PathVariable Long id) { return service.submit(id); }

    @PostMapping("/{id}/approve")
    public DocumentResponse approve(@PathVariable Long id) { return service.approve(id); }

    @PostMapping("/{id}/reject")
    public DocumentResponse reject(@PathVariable Long id, @RequestBody RejectRequest body) {
        return service.reject(id, body.reason());
    }

    @PostMapping("/{id}/renew")
    public DocumentResponse renew(@PathVariable Long id, @Valid @RequestBody RenewRequest body) {
        return service.renew(id, body.newExpiryDate());
    }

    @PostMapping("/{id}/upload")
    public DocumentResponse upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.uploadFile(id, file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @GetMapping("/dashboard/stats")
    public DashboardStatsDto dashboardStats() {
        return service.dashboardStats();
    }
    
}