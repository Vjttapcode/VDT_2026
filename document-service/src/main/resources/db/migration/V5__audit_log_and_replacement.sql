-- ============================================================
-- Mở rộng approval_requests thành AUDIT LOG đầy đủ:
--   - thêm action CREATE / UPDATE / REPLACE
--   - cột changes: JSON {field: {old, new}} lưu giá trị trước/sau khi sửa
-- Và liên kết THAY THẾ văn bản: documents.supersedes_id (logical ref).
-- ============================================================

-- ---- audit log ---------------------------------------------
ALTER TABLE approval_requests ADD COLUMN changes TEXT;

ALTER TABLE approval_requests DROP CONSTRAINT chk_approval_action;
ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_action
    CHECK (action IN ('CREATE', 'UPDATE', 'SUBMIT', 'APPROVE', 'REJECT', 'RENEW', 'REPLACE'));

-- ---- thay thế văn bản --------------------------------------
-- supersedes_id: văn bản HIỆN TẠI thay thế văn bản nào (logical ref, không FK cứng)
ALTER TABLE documents ADD COLUMN supersedes_id BIGINT;
CREATE INDEX idx_documents_supersedes ON documents (supersedes_id);
