package com.vdt.document_service.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.document_service.client.AuthClient;
import com.vdt.document_service.dto.AdminAnalyticsDto;
import com.vdt.document_service.dto.AdminOverrideRequest;
import com.vdt.document_service.dto.AuditLogDto;
import com.vdt.document_service.dto.DashboardStatsDto;
import com.vdt.document_service.dto.DocumentRequest;
import com.vdt.document_service.dto.DocumentResponse;
import com.vdt.document_service.dto.ExpiringDocumentDto;
import com.vdt.document_service.dto.RelationDto;
import com.vdt.document_service.entity.ApprovalRequest;
import com.vdt.document_service.entity.Document;
import com.vdt.document_service.entity.DocumentRelation;
import com.vdt.document_service.entity.DocumentStatus;
import com.vdt.document_service.entity.NotificationOutbox;
import com.vdt.document_service.entity.RelationType;
import com.vdt.document_service.exception.BusinessException;
import com.vdt.document_service.exception.ForbiddenException;
import com.vdt.document_service.exception.NotFoundException;
import com.vdt.document_service.repository.ApprovalRequestRepository;
import com.vdt.document_service.repository.DocumentRelationRepository;
import com.vdt.document_service.repository.DocumentRepository;
import com.vdt.document_service.repository.NotificationOutboxRepository;
import com.vdt.document_service.util.SecurityUtil;

@Service
public class DocumentService {

    private final DocumentRepository repo;
    private final ApprovalRequestRepository approvalRepo;
    private final NotificationOutboxRepository outboxRepo;
    private final DocumentRelationRepository relationRepo;
    private final ObjectMapper objectMapper;
    private final String uploadDir;
    private final AuthClient authClient;
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final List<DocumentStatus> ALERT_STATUSES = List.of(DocumentStatus.ACTIVE, DocumentStatus.WARNING, DocumentStatus.EXPIRED); 
    private static final Set<DocumentStatus> ALERTABLE = Set.of(DocumentStatus.WARNING, DocumentStatus.EXPIRED);

    public DocumentService(DocumentRepository repo, ApprovalRequestRepository approvalRepo,
        NotificationOutboxRepository outboxRepo, DocumentRelationRepository relationRepo,
        ObjectMapper objectMapper,
        @Value("${app.upload-dir:uploads}") String uploadDir, AuthClient authClient) {
        this.repo = repo;
        this.approvalRepo = approvalRepo;
        this.outboxRepo = outboxRepo;
        this.relationRepo = relationRepo;
        this.objectMapper = objectMapper;
        this.uploadDir = uploadDir;
        this.authClient = authClient;
    }

    /** List filter theo role của người gọi. */
    @Transactional
    public List<DocumentResponse> list() {
        activateDueDocuments();   // tự chuyển APPROVED -> ACTIVE cho văn bản đã tới hạn, không chờ cron
        String role = SecurityUtil.currentRole();
        List<Document> docs = switch (role) {
            case "ADMIN"           -> repo.findAll();
            case "MANAGER_COMPANY" -> repo.findByCompanyId(SecurityUtil.currentCompanyId());
            case "MANAGER_CENTER"  -> repo.findByDepartmentId(SecurityUtil.currentDepartmentId());
            default                -> repo.findByOwnerId(SecurityUtil.currentUserId()); // USER
        };
        // cache tên theo ownerId để tra cứu/hiển thị người phụ trách, tránh gọi auth-service trùng
        Map<Long, String> nameCache = new HashMap<>();
        return docs.stream()
                .map(d -> DocumentResponse.from(d,
                        nameCache.computeIfAbsent(d.getOwnerId(), authClient::getName)))
                .toList();
    }

    @Transactional
    public DocumentResponse get(Long id) {
        Document doc = findOrThrow(id);
        assertCanView(doc);
        return DocumentResponse.from(doc, authClient.getName(doc.getOwnerId()));
    }

    @Transactional
    public DocumentResponse create(DocumentRequest req) {
        assertEffectiveBeforeExpiry(req.effectiveDate(), req.expiryDate());
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
                .effectiveDate(req.effectiveDate())
                .renewalCount(0)
                .build();
        Document saved = repo.save(doc);
        saveLog(saved.getId(), "CREATE", userId, null, "Tạo văn bản");   // ai tạo + thời điểm tạo
        return DocumentResponse.from(saved, authClient.getName(userId));
    }

    @Transactional
    public DocumentResponse update(Long id, DocumentRequest req) {
        Document doc = findOrThrow(id);
        assertOwner(doc);                              // chỉ chủ sở hữu sửa
        if (doc.getStatus() != DocumentStatus.DRAFT && doc.getStatus() != DocumentStatus.REJECTED)
            throw new ForbiddenException("Chỉ sửa được văn bản ở trạng thái DRAFT/REJECTED");
        assertEffectiveBeforeExpiry(req.effectiveDate(), req.expiryDate());

        // gom diff giá trị trước/sau trước khi ghi đè
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!Objects.equals(doc.getTitle(), req.title()))
            changes.put("title", pair(doc.getTitle(), req.title()));
        if (!Objects.equals(doc.getDescription(), req.description()))
            changes.put("description", pair(doc.getDescription(), req.description()));
        if (doc.getType() != req.type())
            changes.put("type", pair(doc.getType().name(), req.type().name()));
        if (doc.getLevel() != req.level())
            changes.put("level", pair(doc.getLevel().name(), req.level().name()));
        if (!Objects.equals(doc.getExpiryDate(), req.expiryDate()))
            changes.put("expiryDate", pair(str(doc.getExpiryDate()), str(req.expiryDate())));
        if (!Objects.equals(doc.getEffectiveDate(), req.effectiveDate()))
            changes.put("effectiveDate", pair(str(doc.getEffectiveDate()), str(req.effectiveDate())));

        doc.setTitle(req.title());
        doc.setDescription(req.description());
        doc.setType(req.type());
        doc.setLevel(req.level());
        doc.setExpiryDate(req.expiryDate());
        doc.setEffectiveDate(req.effectiveDate());
        Document saved = repo.save(doc);
        if (!changes.isEmpty())   // chỉ ghi log khi thực sự có thay đổi
            saveLog(saved.getId(), "UPDATE", SecurityUtil.currentUserId(), null, "Sửa văn bản", toJson(changes));
        return DocumentResponse.from(saved, authClient.getName(saved.getOwnerId()));
    }

    @Transactional
    public void delete(Long id) {
        Document doc = findOrThrow(id);
        assertOwner(doc);
        // approval_requests / notification_outbox / document_relations tham chiếu documents không có ON DELETE CASCADE
        approvalRepo.deleteByDocumentId(id);
        outboxRepo.deleteByDocumentId(id);
        relationRepo.deleteByFromDocIdOrToDocId(id, id);
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

    /**
     * Duyệt văn bản: issuedDate = ngày duyệt (ngày ban hành).
     * effectiveDate null hoặc <= hôm nay -> ACTIVE ngay; tương lai -> APPROVED chờ job kích hoạt.
     */
    @Transactional
    public DocumentResponse approve(Long id){
        Document doc = findOrThrow(id);
        assertCanApprove(doc);
        LocalDate today = LocalDate.now();
        boolean effectiveNow = doc.getEffectiveDate() == null || !doc.getEffectiveDate().isAfter(today);
        DocumentStatus newStatus = effectiveNow ? DocumentStatus.ACTIVE : DocumentStatus.APPROVED;
        doc.setIssuedDate(today);
        doc.setStatus(newStatus);
        repo.save(doc);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", pair(DocumentStatus.PENDING.name(), newStatus.name()));
        changes.put("issuedDate", pair(null, str(today)));
        saveLog(doc.getId(), "APPROVE", null, SecurityUtil.currentUserId(),
                effectiveNow ? null : "Chờ hiệu lực từ " + doc.getEffectiveDate(), toJson(changes));
        enqueue(doc, "APPROVED", "USER", null);
        return DocumentResponse.from(doc);
    }

    /**
     * Đổi ngày hiệu lực thủ công cho văn bản APPROVED (quyền như gia hạn).
     * Đặt <= hôm nay = kích hoạt ngay, không cần chờ job.
     */
    @Transactional
    public DocumentResponse setEffectiveDate(Long id, LocalDate newDate) {
        Document doc = findOrThrow(id);
        if (doc.getStatus() != DocumentStatus.APPROVED)
            throw new BusinessException("Chỉ đổi được ngày hiệu lực của văn bản APPROVED (đã duyệt, chờ hiệu lực)");
        if (!canRenew(doc))
            throw new ForbiddenException("Không đủ quyền đổi ngày hiệu lực văn bản này");
        if (!newDate.isBefore(doc.getExpiryDate()))
            throw new BusinessException("Ngày hiệu lực phải trước ngày hết hạn " + doc.getExpiryDate());

        LocalDate oldDate = doc.getEffectiveDate();
        doc.setEffectiveDate(newDate);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("effectiveDate", pair(str(oldDate), str(newDate)));

        boolean activateNow = !newDate.isAfter(LocalDate.now());
        if (activateNow) {
            doc.setStatus(DocumentStatus.ACTIVE);
            changes.put("status", pair(DocumentStatus.APPROVED.name(), DocumentStatus.ACTIVE.name()));
        }
        repo.save(doc);
        saveLog(doc.getId(), "SET_EFFECTIVE", SecurityUtil.currentUserId(), null,
                activateNow ? "Kích hoạt hiệu lực từ " + newDate : "Dời ngày hiệu lực sang " + newDate,
                toJson(changes));
        if (activateNow) enqueue(doc, "EFFECTIVE", "USER", null);
        return DocumentResponse.from(doc, authClient.getName(doc.getOwnerId()));
    }

    /**
     * Quét toàn bộ APPROVED có effective_date <= hôm nay -> ACTIVE (cron gọi định kỳ, hoặc
     * list()/dashboardStats()/findExpiring() gọi để tự làm mới trước khi đọc). Trả về số văn bản đã kích hoạt.
     */
    @Transactional
    public int activateDueDocuments() {
        List<Document> due = repo.findByStatusAndEffectiveDateLessThanEqual(
                DocumentStatus.APPROVED, LocalDate.now());
        int activated = 0;
        for (Document doc : due) {
            if (activateIfDue(doc)) activated++;
        }
        return activated;
    }

    /**
     * Tự chuyển APPROVED -> ACTIVE nếu văn bản đã tới hạn hiệu lực nhưng cron/luồng khác chưa kịp xử lý.
     * Trả về true nếu chính lần gọi này đã kích hoạt (để gọi nơi khác biết có ghi audit/gửi mail hay không).
     */
    private boolean activateIfDue(Document doc) {
        if (doc.getStatus() != DocumentStatus.APPROVED || doc.getEffectiveDate() == null
                || doc.getEffectiveDate().isAfter(LocalDate.now())) {
            return false;
        }
        if (repo.activateIfDueAtomic(doc.getId(), LocalDate.now()) == 0) return false; // thua race, bên khác đã xử lý
        doc.setStatus(DocumentStatus.ACTIVE);   // đồng bộ lại object trong bộ nhớ để response/caller phía sau đọc đúng
        saveLog(doc.getId(), "EFFECTIVE", null, null,
                "Tự động có hiệu lực từ " + doc.getEffectiveDate(),
                toJson(oneChange("status", DocumentStatus.APPROVED.name(), DocumentStatus.ACTIVE.name())));
        enqueue(doc, "EFFECTIVE", "USER", null);
        return true;
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

    @Transactional
    public DocumentResponse renew(Long id, LocalDate newExpiryDate) {
        Document doc = findOrThrow(id);
        if (newExpiryDate.isBefore(LocalDate.now()))
            throw new BusinessException("Ngày gia hạn phải sau hôm nay");
        if (doc.getStatus() != DocumentStatus.WARNING && doc.getStatus() != DocumentStatus.EXPIRED && doc.getStatus() != DocumentStatus.ACTIVE)
            throw new BusinessException("Không gia hạn được văn bản ở trạng thái " + doc.getStatus());
        if (!canRenew(doc))
            throw new ForbiddenException("Không đủ quyền gia hạn văn bản này");

        LocalDate oldExpiry = doc.getExpiryDate();
        doc.setExpiryDate(newExpiryDate);
        doc.setStatus(DocumentStatus.ACTIVE);
        doc.setRenewalCount(doc.getRenewalCount() + 1);
        repo.save(doc);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("expiryDate", pair(str(oldExpiry), str(newExpiryDate)));
        saveLog(doc.getId(), "RENEW", SecurityUtil.currentUserId(), null,
                "Gia hạn lần " + doc.getRenewalCount() + " đến " + newExpiryDate, toJson(changes));
        return DocumentResponse.from(doc, authClient.getName(doc.getOwnerId()));
    }

    /**
     * Tạo quan hệ nghiệp vụ: văn bản {id} {type} văn bản {targetId}; ghi audit 2 chiều.
     * REPLACE/REPEAL → văn bản cũ (target) mất hiệu lực (ACTIVE/WARNING chuyển EXPIRED).
     * REPLACE → đồng thời set supersedes_id (giữ tương thích V5). AMEND → chỉ liên kết, không đổi trạng thái.
     */
    @Transactional
    public DocumentResponse relate(Long id, Long targetId, RelationType type) {
        if (id.equals(targetId))
            throw new BusinessException("Văn bản không thể tự liên kết với chính nó");
        Document doc = findOrThrow(id);
        Document target = findOrThrow(targetId);
        if (!canRenew(doc))   // cùng quyền như gia hạn: chủ sở hữu / admin / manager trong phạm vi
            throw new ForbiddenException("Không đủ quyền tạo quan hệ cho văn bản này");
        assertCanView(target);
        if (relationRepo.existsByFromDocIdAndToDocIdAndRelationType(id, targetId, type))
            throw new BusinessException("Quan hệ này đã tồn tại");

        Long actor = SecurityUtil.currentUserId();
        relationRepo.save(DocumentRelation.builder()
                .fromDocId(id).toDocId(targetId).relationType(type).createdBy(actor).build());

        // audit phía văn bản tác động (from); REPLACE ghi kèm supersedes_id để tương thích V5
        String fromChanges = null;
        if (type == RelationType.REPLACE) {
            doc.setSupersedesId(targetId);
            repo.save(doc);
            fromChanges = toJson(oneChange("supersedesId", null, targetId));
        }
        saveLog(id, type.name(), actor, null, fromLabel(type) + " văn bản #" + targetId, fromChanges);

        // REPLACE/REPEAL: văn bản cũ mất hiệu lực (kể cả APPROVED chưa kịp hiệu lực) + ghi diff trạng thái
        String toChanges = null;
        if (type != RelationType.AMEND
                && (target.getStatus() == DocumentStatus.ACTIVE || target.getStatus() == DocumentStatus.WARNING
                    || target.getStatus() == DocumentStatus.APPROVED)) {
            DocumentStatus oldStatus = target.getStatus();
            target.setStatus(DocumentStatus.EXPIRED);
            repo.save(target);
            toChanges = toJson(oneChange("status", oldStatus.name(), DocumentStatus.EXPIRED.name()));
        }
        saveLog(targetId, type.name(), actor, null, toLabel(type) + " văn bản #" + id, toChanges);

        return DocumentResponse.from(doc, authClient.getName(doc.getOwnerId()));
    }

    /** Endpoint /replace cũ — ủy quyền sang relate() với quan hệ REPLACE. */
    @Transactional
    public DocumentResponse replace(Long id, Long supersededId) {
        return relate(id, supersededId, RelationType.REPLACE);
    }

    /** Lịch sử thay đổi (audit log) của một văn bản — mới nhất trước. */
    @Transactional
    public List<AuditLogDto> history(Long id) {
        Document doc = findOrThrow(id);
        assertCanView(doc);
        Map<Long, String> nameCache = new HashMap<>();
        return approvalRepo.findByDocumentIdOrderByCreatedAtDesc(id).stream()
                .map(a -> {
                    Long actorId = a.getReviewerId() != null ? a.getReviewerId() : a.getRequesterId();
                    String name = actorId == null ? null : nameCache.computeIfAbsent(actorId, authClient::getName);
                    return AuditLogDto.from(a, name);
                }).toList();
    }

    /** Danh sách quan hệ của một văn bản (cả 2 chiều), đã resolve văn bản đối tác. */
    @Transactional
    public List<RelationDto> relations(Long id) {
        Document doc = findOrThrow(id);
        assertCanView(doc);
        return relationRepo.findByDoc(id).stream().map(r -> {
            boolean outgoing = r.getFromDocId().equals(id);
            Long otherId = outgoing ? r.getToDocId() : r.getFromDocId();
            String otherTitle = repo.findById(otherId).map(Document::getTitle).orElse(null);
            return new RelationDto(r.getId(), r.getRelationType().name(),
                    outgoing ? "OUTGOING" : "INCOMING", otherId, otherTitle, r.getCreatedAt());
        }).toList();
    }

    @Transactional
    public DocumentResponse uploadFile(Long id, MultipartFile file) {
        Document doc = findOrThrow(id);
        assertOwner(doc);
        if(file == null || file.isEmpty() || !ALLOWED_TYPES.contains(file.getContentType()))
            throw new BusinessException("Chỉ chấp nhận file định dạng PDF hoặc WORD (.doc/.docx)");
        try {
            Files.createDirectories(Paths.get(uploadDir));
            String original = Paths.get(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()).getFileName().toString();
            String filename = UUID.randomUUID() +  "_" + original;
            Files.copy(file.getInputStream(), Paths.get(uploadDir, filename), StandardCopyOption.REPLACE_EXISTING);
            doc.setFilePath("/uploads/" + filename);
            return DocumentResponse.from(repo.save(doc));
        } catch (IOException e){
            throw new BusinessException("Lưu file thất bại: " + e.getMessage());
        }
    }

    @Transactional
    public List<ExpiringDocumentDto> findExpiring(int withinDays) {
        activateDueDocuments();   // tự chuyển APPROVED -> ACTIVE cho văn bản đã tới hạn, để không bỏ sót cảnh báo
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(withinDays);
        Map<Long, String> emailCache = new HashMap<>();

        return repo.findExpiring(ALERT_STATUSES, threshold).stream()
                .map(d -> new ExpiringDocumentDto(
                    d.getId(),
                    d.getLevel().name(),
                    ChronoUnit.DAYS.between(today, d.getExpiryDate()),
                    d.getDepartmentId(),
                    d.getCompanyId(),
                    emailCache.computeIfAbsent(d.getOwnerId(), authClient::getEmail)
                )).toList();
    }

    @Transactional
    public DashboardStatsDto dashboardStats() {
        activateDueDocuments();   // tự chuyển APPROVED -> ACTIVE cho văn bản đã tới hạn, không chờ cron
        String role = SecurityUtil.currentRole();
        List<Document> docs = switch (role) {                 // cùng switch với list()
            case "ADMIN"           -> repo.findAll();
            case "MANAGER_COMPANY" -> repo.findByCompanyId(SecurityUtil.currentCompanyId());
            case "MANAGER_CENTER"  -> repo.findByDepartmentId(SecurityUtil.currentDepartmentId());
            default                -> repo.findByOwnerId(SecurityUtil.currentUserId());   // USER
        };

        // đếm theo trạng thái trong bộ đã lọc theo role
        Map<DocumentStatus, Long> byStatus = docs.stream()
                .collect(Collectors.groupingBy(Document::getStatus, Collectors.counting()));

        // danh sách sắp hết hạn 30 ngày (chỉ ACTIVE/WARNING/EXPIRED), sắp xếp gần hết hạn trước
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(30);
        List<DashboardStatsDto.ExpiringItem> expiring = docs.stream()
            .filter(d -> ALERT_STATUSES.contains(d.getStatus()))
            .filter(d -> d.getExpiryDate() != null && !d.getExpiryDate().isAfter(limit))
            .sorted(Comparator.comparing(Document::getExpiryDate))
            .map(d -> new DashboardStatsDto.ExpiringItem(
                    d.getId(), d.getTitle(), d.getLevel().name(),
                    ChronoUnit.DAYS.between(today, d.getExpiryDate())))
            .toList();

        return new DashboardStatsDto(
            byStatus.getOrDefault(DocumentStatus.ACTIVE,  0L),
            byStatus.getOrDefault(DocumentStatus.WARNING, 0L),
            byStatus.getOrDefault(DocumentStatus.EXPIRED, 0L),
            byStatus.getOrDefault(DocumentStatus.PENDING, 0L),
            byStatus.getOrDefault(DocumentStatus.APPROVED, 0L),
            expiring);
    }

    @Transactional
    public DocumentResponse updateStatus(Long id, DocumentStatus newStatus) {
        if(!ALERTABLE.contains(newStatus))
            throw new BusinessException("Internal API chỉ đặt được WARNING hoặc EXPIRED");

        Document doc = findOrThrow(id);
        if(doc.getStatus() != DocumentStatus.ACTIVE && doc.getStatus() != DocumentStatus.WARNING)
            throw new BusinessException("Không đổi trạng thái từ " + doc.getStatus());
        doc.setStatus(newStatus);
        return DocumentResponse.from(repo.save(doc));
    }

    /** Admin can thiệp: sửa mọi trường ở mọi trạng thái, reassign owner, ép status; ghi audit đầy đủ. */
    @Transactional
    public DocumentResponse adminOverride(Long id, AdminOverrideRequest req) {
        if (!"ADMIN".equals(SecurityUtil.currentRole()))
            throw new ForbiddenException("Chỉ ADMIN mới được can thiệp văn bản");
        Document doc = findOrThrow(id);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (req.title() != null && !req.title().equals(doc.getTitle())) {
            changes.put("title", pair(doc.getTitle(), req.title())); doc.setTitle(req.title());
        }
        if (req.description() != null && !Objects.equals(req.description(), doc.getDescription())) {
            changes.put("description", pair(doc.getDescription(), req.description())); doc.setDescription(req.description());
        }
        if (req.type() != null && req.type() != doc.getType()) {
            changes.put("type", pair(doc.getType().name(), req.type().name())); doc.setType(req.type());
        }
        if (req.level() != null && req.level() != doc.getLevel()) {
            changes.put("level", pair(doc.getLevel().name(), req.level().name())); doc.setLevel(req.level());
        }
        if (req.expiryDate() != null && !req.expiryDate().equals(doc.getExpiryDate())) {
            changes.put("expiryDate", pair(str(doc.getExpiryDate()), str(req.expiryDate()))); doc.setExpiryDate(req.expiryDate());
        }
        if (req.effectiveDate() != null && !req.effectiveDate().equals(doc.getEffectiveDate())) {
            changes.put("effectiveDate", pair(str(doc.getEffectiveDate()), str(req.effectiveDate()))); doc.setEffectiveDate(req.effectiveDate());
        }
        if (req.ownerId() != null && !req.ownerId().equals(doc.getOwnerId())) {
            changes.put("ownerId", pair(doc.getOwnerId(), req.ownerId())); doc.setOwnerId(req.ownerId());
        }
        if (req.status() != null && req.status() != doc.getStatus()) {
            changes.put("status", pair(doc.getStatus().name(), req.status().name())); doc.setStatus(req.status());
        }
        Document saved = repo.save(doc);
        if (!changes.isEmpty())
            saveLog(id, "ADMIN_OVERRIDE", SecurityUtil.currentUserId(), null, "Can thiệp bởi quản trị", toJson(changes));
        return DocumentResponse.from(saved, authClient.getName(saved.getOwnerId()));
    }

    /** Phân tích toàn hệ thống cho ADMIN: theo trạng thái/loại/phòng ban + xu hướng hết hạn theo tháng. */
    @Transactional(readOnly = true)
    public AdminAnalyticsDto adminAnalytics() {
        if (!"ADMIN".equals(SecurityUtil.currentRole()))
            throw new ForbiddenException("Chỉ ADMIN xem được phân tích toàn hệ thống");
        List<Document> docs = repo.findAll();
        Map<String, Long> byStatus = docs.stream()
                .collect(Collectors.groupingBy(d -> d.getStatus().name(), Collectors.counting()));
        Map<String, Long> byType = docs.stream()
                .collect(Collectors.groupingBy(d -> d.getType().name(), Collectors.counting()));

        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(30);
        Map<Long, List<Document>> byDept = docs.stream()
                .filter(d -> d.getDepartmentId() != null)
                .collect(Collectors.groupingBy(Document::getDepartmentId));
        List<AdminAnalyticsDto.DeptStat> deptStats = byDept.entrySet().stream()
                .map(e -> {
                    long total = e.getValue().size();
                    long expired = e.getValue().stream()
                            .filter(d -> d.getExpiryDate() != null && d.getExpiryDate().isBefore(today)).count();
                    long soon = e.getValue().stream()
                            .filter(d -> d.getExpiryDate() != null
                                    && !d.getExpiryDate().isBefore(today) && !d.getExpiryDate().isAfter(limit)).count();
                    return new AdminAnalyticsDto.DeptStat(e.getKey(), total, soon, expired);
                })
                .sorted(Comparator.comparing(AdminAnalyticsDto.DeptStat::departmentId))
                .toList();

        long[] monthly = new long[12];
        int year = today.getYear();
        for (Document d : docs)
            if (d.getExpiryDate() != null && d.getExpiryDate().getYear() == year)
                monthly[d.getExpiryDate().getMonthValue() - 1]++;

        return new AdminAnalyticsDto(docs.size(), byStatus, byType, deptStats, monthly);
    }

    // ---- helpers --------------------------------------------------
    private Document findOrThrow(Long id) {
        Document doc = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy văn bản id=" + id));
        activateIfDue(doc);   // tự chuyển APPROVED -> ACTIVE nếu đã tới hạn, không chờ cron
        return doc;
    }

    private void assertEffectiveBeforeExpiry(LocalDate effectiveDate, LocalDate expiryDate) {
        if (effectiveDate != null && expiryDate != null && !effectiveDate.isBefore(expiryDate))
            throw new BusinessException("Ngày hiệu lực phải trước ngày hết hạn");
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
            case CENTER  -> role.equals("ADMIN")
                    || (role.equals("MANAGER_COMPANY") && SecurityUtil.currentCompanyId().equals(doc.getCompanyId()))
                    || (role.equals("MANAGER_CENTER")  && SecurityUtil.currentDepartmentId().equals(doc.getDepartmentId()));
            case COMPANY -> role.equals("ADMIN")
                    || (role.equals("MANAGER_COMPANY") && SecurityUtil.currentCompanyId().equals(doc.getCompanyId()));
            case GROUP   -> role.equals("ADMIN");
        };
        if(!ok) throw new ForbiddenException("Không đủ quyền duyệt văn bản cấp " + doc.getLevel());
    }

    private void saveLog(Long docId, String action, Long requesterId, Long reviewerId, String comment) {
        saveLog(docId, action, requesterId, reviewerId, comment, null);
    }

    private void saveLog(Long docId, String action, Long requesterId, Long reviewerId, String comment, String changes) {
        approvalRepo.save(ApprovalRequest.builder()
                .documentId(docId).action(action)
                .requesterId(requesterId).reviewerId(reviewerId)
                .comment(comment).changes(changes).build());
    }

    /** {old,new} — dùng LinkedHashMap để chấp nhận giá trị null. */
    private Map<String, Object> pair(Object oldV, Object newV) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("old", oldV);
        m.put("new", newV);
        return m;
    }

    /** Bọc một trường thay đổi thành {field: {old, new}}. */
    private Map<String, Object> oneChange(String field, Object oldV, Object newV) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(field, pair(oldV, newV));
        return m;
    }

    private String str(LocalDate d) { return d == null ? null : d.toString(); }

    /** Nhãn audit phía văn bản tác động (from). */
    private String fromLabel(RelationType t) {
        return switch (t) {
            case REPLACE -> "Thay thế";
            case REPEAL  -> "Bãi bỏ";
            case AMEND   -> "Sửa đổi/bổ sung";
        };
    }

    /** Nhãn audit phía văn bản bị tác động (to). */
    private String toLabel(RelationType t) {
        return switch (t) {
            case REPLACE -> "Được thay thế bởi";
            case REPEAL  -> "Bị bãi bỏ bởi";
            case AMEND   -> "Được sửa đổi/bổ sung bởi";
        };
    }

    private String toJson(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Không tạo được nội dung audit changes");
        }
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
            p.put("ownerId", doc.getOwnerId());
            p.put("departmentId", doc.getDepartmentId());
            p.put("ownerEmail", authClient.getEmail(doc.getOwnerId()));
            if(reason!=null) p.put("reason", reason);
            return objectMapper.writeValueAsString(p);
        }catch (JsonProcessingException e) {
            throw new BusinessException("Không tạo được payload outbox");
        }
    }

    private boolean canRenew(Document doc){
        String role = SecurityUtil.currentRole();
        return doc.getOwnerId().equals(SecurityUtil.currentUserId())
            || "ADMIN".equals(role)
            || ("MANAGER_CENTER".equals(role) && SecurityUtil.currentDepartmentId().equals(doc.getDepartmentId()))
            || ("MANAGER_COMPANY".equals(role) && SecurityUtil.currentCompanyId().equals(doc.getCompanyId()));
    }

    private Long nullIfSentinel(Long v) { return (v == null || v == -1L) ? null : v; }
}