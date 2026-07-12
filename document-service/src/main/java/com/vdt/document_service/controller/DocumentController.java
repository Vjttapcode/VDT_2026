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

import java.util.ArrayList;

import org.springframework.web.bind.annotation.PatchMapping;

import com.vdt.document_service.dto.AdminAnalyticsDto;
import com.vdt.document_service.dto.AuditLogDto;
import com.vdt.document_service.dto.BulkIdsRequest;
import com.vdt.document_service.dto.BulkRenewRequest;
import com.vdt.document_service.dto.BulkResult;
import com.vdt.document_service.dto.DashboardStatsDto;
import com.vdt.document_service.dto.DocumentRequest;
import com.vdt.document_service.dto.DocumentResponse;
import com.vdt.document_service.dto.DocumentVersionDto;
import com.vdt.document_service.dto.EffectiveDateRequest;
import com.vdt.document_service.dto.RejectRequest;
import com.vdt.document_service.dto.RelateRequest;
import com.vdt.document_service.dto.RelationDto;
import com.vdt.document_service.dto.RenewRequest;
import com.vdt.document_service.dto.ReplaceRequest;
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

    /** Đổi ngày hiệu lực (văn bản APPROVED); đặt <= hôm nay = kích hoạt ngay. */
    @PatchMapping("/{id}/effective-date")
    public DocumentResponse setEffectiveDate(@PathVariable Long id, @Valid @RequestBody EffectiveDateRequest body) {
        return service.setEffectiveDate(id, body.effectiveDate());
    }

    @PostMapping("/{id}/replace")
    public DocumentResponse replace(@PathVariable Long id, @Valid @RequestBody ReplaceRequest body) {
        return service.replace(id, body.supersededId());
    }

    @PostMapping("/{id}/relate")
    public DocumentResponse relate(@PathVariable Long id, @Valid @RequestBody RelateRequest body) {
        return service.relate(id, body.targetId(), body.type());
    }

    @GetMapping("/{id}/relations")
    public List<RelationDto> relations(@PathVariable Long id) {
        return service.relations(id);
    }

    @GetMapping("/{id}/history")
    public List<AuditLogDto> history(@PathVariable Long id) {
        return service.history(id);
    }

    /** Mở lại văn bản đã ban hành về DRAFT để sửa đổi rồi nộp duyệt lại (tái ban hành). */
    @PostMapping("/{id}/reopen")
    public DocumentResponse reopen(@PathVariable Long id) { return service.reopen(id); }

    /** Lịch sử phiên bản (snapshot mỗi lần ban hành) — mới nhất trước. */
    @GetMapping("/{id}/versions")
    public List<DocumentVersionDto> versions(@PathVariable Long id) {
        return service.versions(id);
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

    /** Số liệu phân tích toàn hệ thống (ADMIN). */
    @GetMapping("/admin/analytics")
    public AdminAnalyticsDto adminAnalytics() {
        return service.adminAnalytics();
    }

    /** Gia hạn hàng loạt — mỗi văn bản một transaction riêng, lỗi văn bản này không ảnh hưởng văn bản khác. */
    @PostMapping("/bulk/renew")
    public BulkResult bulkRenew(@Valid @RequestBody BulkRenewRequest body) {
        List<String> errors = new ArrayList<>();
        int ok = 0;
        for (Long id : body.ids()) {
            try { service.renew(id, body.newExpiryDate()); ok++; }
            catch (RuntimeException e) { errors.add("#" + id + ": " + e.getMessage()); }
        }
        return new BulkResult(ok, errors.size(), errors);
    }

    /** Phê duyệt hàng loạt. */
    @PostMapping("/bulk/approve")
    public BulkResult bulkApprove(@Valid @RequestBody BulkIdsRequest body) {
        List<String> errors = new ArrayList<>();
        int ok = 0;
        for (Long id : body.ids()) {
            try { service.approve(id); ok++; }
            catch (RuntimeException e) { errors.add("#" + id + ": " + e.getMessage()); }
        }
        return new BulkResult(ok, errors.size(), errors);
    }

}