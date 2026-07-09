-- ============================================================
-- Phiên bản văn bản (document versioning):
--   - documents.version_major/version_minor: bắt đầu v1.0 khi tạo;
--     mỗi lần TÁI BAN HÀNH (duyệt lại sau reopen) tăng minor +0.1
--   - document_versions: snapshot đầy đủ nội dung từng lần ban hành
--     (kể cả file đính kèm) để xem/tải lại
--   - action mới REOPEN: mở lại văn bản ACTIVE/WARNING/EXPIRED về DRAFT
--     để sửa đổi rồi nộp duyệt lại
-- Versioning độc lập với thay thế văn bản (supersedes_id/document_relations).
-- ============================================================

ALTER TABLE documents ADD COLUMN version_major SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE documents ADD COLUMN version_minor SMALLINT NOT NULL DEFAULT 0;

CREATE TABLE document_versions (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES documents (id),
    version_major   SMALLINT NOT NULL,
    version_minor   SMALLINT NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    type            VARCHAR(30) NOT NULL,
    level           VARCHAR(20) NOT NULL,
    file_path       VARCHAR(500),
    effective_date  DATE,
    expiry_date     DATE NOT NULL,
    issued_date     DATE,
    created_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_versions_doc ON document_versions (document_id, version_major, version_minor);

-- backfill: văn bản đã từng ban hành có sẵn snapshot v1.0 từ nội dung hiện tại
INSERT INTO document_versions (document_id, version_major, version_minor, title, description,
                               type, level, file_path, effective_date, expiry_date, issued_date, created_at)
SELECT id, 1, 0, title, description, type, level, file_path, effective_date, expiry_date, issued_date, NOW()
FROM documents
WHERE issued_date IS NOT NULL;

-- audit log: REOPEN (mở lại văn bản đã ban hành để sửa đổi/tái ban hành)
ALTER TABLE approval_requests DROP CONSTRAINT chk_approval_action;
ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_action
    CHECK (action IN ('CREATE', 'UPDATE', 'SUBMIT', 'APPROVE', 'REJECT', 'RENEW',
                      'REPLACE', 'REPEAL', 'AMEND', 'ADMIN_OVERRIDE',
                      'EFFECTIVE', 'SET_EFFECTIVE', 'REOPEN'));
