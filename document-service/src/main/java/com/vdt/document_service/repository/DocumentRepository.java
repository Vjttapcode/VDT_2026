package com.vdt.document_service.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vdt.document_service.entity.Document;
import com.vdt.document_service.entity.DocumentStatus;


public interface DocumentRepository extends  JpaRepository<Document, Long>{
    List<Document> findByOwnerId(Long ownerId);

    List<Document> findByDepartmentId(Long departmentId);

    List<Document> findByCompanyId(Long companyId);

    @Query("""
       SELECT d FROM Document d
       WHERE d.status IN :statuses
         AND d.expiryDate <= :threshold
       ORDER BY d.expiryDate ASC
       """)
    List<Document> findExpiring(@Param("statuses") List<DocumentStatus> statuses,
                            @Param("threshold") LocalDate threshold);

    /** Văn bản APPROVED đã tới ngày hiệu lực — cho job tự động kích hoạt hằng ngày. */
    List<Document> findByStatusAndEffectiveDateLessThanEqual(DocumentStatus status, LocalDate date);

    /**
     * UPDATE atomic ở DB: chỉ chuyển APPROVED -> ACTIVE nếu vẫn còn đúng điều kiện tại thời điểm chạy.
     * Dùng làm "chốt thắng" khi cron và luồng self-heal (đọc văn bản) cùng kiểm tra 1 văn bản gần như đồng thời —
     * bên nào UPDATE được (rows=1) mới ghi audit/gửi mail, bên thua race thấy rows=0 và bỏ qua,
     * tránh audit log / email trùng lặp do check-then-act ở tầng Java không atomic.
     */
    @Modifying
    @Query("""
        UPDATE Document d SET d.status = com.vdt.document_service.entity.DocumentStatus.ACTIVE
        WHERE d.id = :id
          AND d.status = com.vdt.document_service.entity.DocumentStatus.APPROVED
          AND d.effectiveDate <= :today
        """)
    int activateIfDueAtomic(@Param("id") Long id, @Param("today") LocalDate today);
}

