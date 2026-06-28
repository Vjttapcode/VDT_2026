package com.vdt.document_service.service;

import com.vdt.document_service.dto.DocumentRequest;
import com.vdt.document_service.dto.DocumentResponse;
import com.vdt.document_service.entity.*;
import com.vdt.document_service.exception.ForbiddenException;
import com.vdt.document_service.exception.NotFoundException;
import com.vdt.document_service.repository.DocumentRepository;
import com.vdt.document_service.util.SecurityUtil;
import com.vdt.document_service.exception.BusinessException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.vdt.document_service.repository.ApprovalRequestRepository;
import com.vdt.document_service.repository.NotificationOutboxRepository;

@Service
public class DocumentService {

    private final DocumentRepository repo;
    private final ApprovalRequestRepository approvalRepo;
    private final NotificationOutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    

    public DocumentService(DocumentRepository repo, ApprovalRequestRepository approvalRepo, NotificationOutboxRepository outboxRepo, ObjectMapper objectMapper) { 
        this.repo = repo;
        this.approvalRepo = approvalRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper; 
    }

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

    @Transactional
    public DocumentResponse submit(Long id) {
        Document doc = findOrThrow(id);
        assertOwner(doc);
        if (doc.getStatus() != DocumentStatus.DRAFT && doc.getStatus() != DocumentStatus.REJECTED)
            throw new BusinessException("Chỉ nộp được văn bản DRAFT/REJECTED");
        
        doc.setStatus(DocumentStatus.PENDING);
        repo.save(doc);
        saveLog(doc.getId(), "SUBMIT", SecurityUtil.currentUserId(), null, null);
        enqueue(doc, "APPROVAL_REQUEST", "MANAGER_CENTER", null);
        return DocumentResponse.from(doc);
    }

    @Transactional
    public DocumentResponse approve(Long id){
        Document doc = findOrThrow(id);
        assertCanApprove(doc);
        doc.setStatus(DocumentStatus.ACTIVE);
        repo.save(doc);
        saveLog(doc.getId(), "APPROVE", null, SecurityUtil.currentUserId(), null);
        enqueue(doc, "APPROVED", "USER", null);
        return DocumentResponse.from(doc);
    }

    @Transactional
    public DocumentResponse reject(Long id, String reason) {
        Document doc = findOrThrow(id);
        assertCanApprove(doc);
        doc.setStatus(DocumentStatus.REJECTED);
        repo.save(doc);
        saveLog(doc.getId(), "REJECT", null, SecurityUtil.currentUserId(), reason);
        enqueue(doc, "REJECTED", "USER", reason);
        return DocumentResponse.from(doc);
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

    private void assertCanApprove(Document doc){
        if(doc.getStatus() != DocumentStatus.PENDING)
            throw new BusinessException("Chỉ duyệt/từ chối được văn bản trạng thái PENDING");
        if(doc.getOwnerId().equals(SecurityUtil.currentUserId()))
            throw new ForbiddenException("Không được tự duyệt văn bản của mình");
        String role = SecurityUtil.currentRole();
        boolean ok = switch(doc.getLevel()){
            case CENTER -> !"USER".equals(role) && (role.equals("ADMIN")) || role.equals("MANAGER_COMPANY") || doc.getDepartmentId().equals(SecurityUtil.currentDepartmentId());
            case COMPANY -> role.equals("ADMIN") || role.equals("MANAGER_COMPANY") && doc.getCompanyId().equals(SecurityUtil.currentCompanyId());
            case GROUP -> role.equals("ADMIN");
        };
        if(!ok) throw new ForbiddenException("Không đủ quyền duyệt văn bản cấp " + doc.getLevel());
    }

    private void saveLog(Long docId, String action, Long requesterId, Long reviewerId, String comment) {
        approvalRepo.save(ApprovalRequest.builder()
                .documentId(docId).action(action)
                .requesterId(requesterId).reviewerId(reviewerId)
                .comment(comment).build());
    }

    private void enqueue(Document doc, String eventType, String recipientRole, String reason){
        outboxRepo.save(NotificationOutbox.builder()
                .eventType(eventType)
                .documentId(doc.getId())
                .payload(buildPayload(doc, recipientRole, reason))
                .build());
    }

    private String buildPayload(Document doc, String recipientRole, String reason){
        try{
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("docId", doc.getId());
            p.put("docTitle", doc.getTitle());
            p.put("level", doc.getLevel().name());
            p.put("expiryDate", doc.getExpiryDate().toString());
            p.put("recipientRole", recipientRole);
            if(reason!=null) p.put("reason", reason);
            return objectMapper.writeValueAsString(p);
        }catch (JsonProcessingException e) {
            throw new BusinessException("Không tạo được payload outbox");
        }
    }

    private Long nullIfSentinel(Long v) { return (v == null || v == -1L) ? null : v; }
}