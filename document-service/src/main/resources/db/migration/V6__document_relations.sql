-- ============================================================
-- Quan hệ nghiệp vụ giữa các văn bản (tổng quát 3 loại):
--   REPLACE  — thay thế
--   REPEAL   — bãi bỏ
--   AMEND    — sửa đổi / bổ sung
-- from_doc_id = văn bản TÁC ĐỘNG (mới); to_doc_id = văn bản BỊ TÁC ĐỘNG (cũ).
-- supersedes_id (V5) vẫn giữ, được ghi song song khi tạo quan hệ REPLACE.
-- ============================================================
CREATE TABLE document_relations (
    id            BIGSERIAL   PRIMARY KEY,
    from_doc_id   BIGINT      NOT NULL,          -- văn bản mới (tác động)
    to_doc_id     BIGINT      NOT NULL,          -- văn bản cũ (bị tác động)
    relation_type VARCHAR(20) NOT NULL,
    created_by    BIGINT,
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT chk_relation_type
        CHECK (relation_type IN ('REPLACE', 'REPEAL', 'AMEND')),
    CONSTRAINT uq_relation UNIQUE (from_doc_id, to_doc_id, relation_type)
);

CREATE INDEX idx_relation_from ON document_relations (from_doc_id);
CREATE INDEX idx_relation_to   ON document_relations (to_doc_id);

-- Bổ sung action REPEAL / AMEND cho audit log (V5 đã có REPLACE)
ALTER TABLE approval_requests DROP CONSTRAINT chk_approval_action;
ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_action
    CHECK (action IN ('CREATE', 'UPDATE', 'SUBMIT', 'APPROVE', 'REJECT', 'RENEW', 'REPLACE', 'REPEAL', 'AMEND'));
