package com.vdt.document_service.service;

import com.vdt.document_service.dto.DocumentRequest;
import com.vdt.document_service.dto.DocumentResponse;
import com.vdt.document_service.entity.*;
import com.vdt.document_service.exception.ForbiddenException;
import com.vdt.document_service.exception.NotFoundException;
import com.vdt.document_service.repository.DocumentRepository;
import com.vdt.document_service.util.SecurityUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final DocumentRepository repo;

    public DocumentService(DocumentRepository repo) { this.repo = repo; }

    /** List filter theo role của người gọi. */
    public List<DocumentResponse> list() {
        String role = SecurityUtil.currentRole();
        List<Document> docs = switch (role) {
            case "ADMIN"           -> repo.findAll();
            case "MANAGER_COMPANY" -> repo.findByCompanyId(SecurityUtil.currentCompanyId());
            case "MANAGER_CENTER"  -> repo.findByDepartmentId(SecurityUtil.currentDepartmentId());
            default                -> repo.findByOwnerId(SecurityUtil.currentUserId()); // USER
        };
        return docs.stream().map(DocumentResponse::from).toList();
    }

    public DocumentResponse get(Long id) {
        Document doc = findOrThrow(id);
        assertCanView(doc);
        return DocumentResponse.from(doc);
    }

    @Transactional
    public DocumentResponse create(DocumentRequest req) {
        Long userId = SecurityUtil.currentUserId();
        Document doc = Document.builder()
                .title(req.title())
                .description(req.description())
                .type(req.type())
                .level(req.level())
                .status(DocumentStatus.DRAFT)
                .ownerId(userId)
                .departmentId(nullIfSentinel(SecurityUtil.currentDepartmentId()))
                .companyId(nullIfSentinel(SecurityUtil.currentCompanyId()))
                .expiryDate(req.expiryDate())
                .renewalCount(0)
                .build();
        return DocumentResponse.from(repo.save(doc));
    }

    @Transactional
    public DocumentResponse update(Long id, DocumentRequest req) {
        Document doc = findOrThrow(id);
        assertOwner(doc);                              // chỉ chủ sở hữu sửa
        if (doc.getStatus() != DocumentStatus.DRAFT && doc.getStatus() != DocumentStatus.REJECTED)
            throw new ForbiddenException("Chỉ sửa được văn bản ở trạng thái DRAFT/REJECTED");
        doc.setTitle(req.title());
        doc.setDescription(req.description());
        doc.setType(req.type());
        doc.setLevel(req.level());
        doc.setExpiryDate(req.expiryDate());
        return DocumentResponse.from(repo.save(doc));
    }

    @Transactional
    public void delete(Long id) {
        Document doc = findOrThrow(id);
        assertOwner(doc);
        repo.delete(doc);
    }

    // ---- helpers --------------------------------------------------
    private Document findOrThrow(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy văn bản id=" + id));
    }

    private void assertOwner(Document doc) {
        if (!doc.getOwnerId().equals(SecurityUtil.currentUserId()))
            throw new ForbiddenException("Bạn không phải chủ sở hữu văn bản này");
    }

    /** ADMIN xem hết; MANAGER xem trong phạm vi; USER chỉ xem của mình. */
    private void assertCanView(Document doc) {
        String role = SecurityUtil.currentRole();
        boolean ok = switch (role) {
            case "ADMIN"           -> true;
            case "MANAGER_COMPANY" -> SecurityUtil.currentCompanyId().equals(doc.getCompanyId());
            case "MANAGER_CENTER"  -> SecurityUtil.currentDepartmentId().equals(doc.getDepartmentId());
            default                -> SecurityUtil.currentUserId().equals(doc.getOwnerId());
        };
        if (!ok) throw new ForbiddenException("Không có quyền xem văn bản này");
    }

    private Long nullIfSentinel(Long v) { return (v == null || v == -1L) ? null : v; }
}