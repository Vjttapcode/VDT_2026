package com.vdt.document_service.controller;

import com.vdt.document_service.dto.DocumentRequest;
import com.vdt.document_service.dto.DocumentResponse;
import com.vdt.document_service.service.DocumentService;
import com.vdt.document_service.dto.RejectRequest;
import com.vdt.document_service.dto.RenewRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}