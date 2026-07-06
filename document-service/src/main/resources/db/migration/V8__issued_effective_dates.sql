-- ============================================================
-- Ngày ban hành & ngày có hiệu lực:
--   - issued_date   : ngày cấp trên phê duyệt (hệ thống tự set khi APPROVE)
--   - effective_date: ngày văn bản bắt đầu có hiệu lực (người tạo nhập,
--                     null = có hiệu lực ngay khi được duyệt)
--   - status APPROVED: đã duyệt, chờ đến ngày hiệu lực
--     lifecycle mới: DRAFT -> PENDING -> APPROVED -> ACTIVE -> WARNING -> EXPIRED
--                                     \-> ACTIVE (effective_date <= ngày duyệt)
-- ============================================================

ALTER TABLE documents ADD COLUMN issued_date    DATE;
ALTER TABLE documents ADD COLUMN effective_date DATE;

ALTER TABLE documents DROP CONSTRAINT chk_doc_status;
ALTER TABLE documents ADD CONSTRAINT chk_doc_status
    CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'ACTIVE', 'WARNING', 'EXPIRED', 'REJECTED'));

-- backfill: văn bản đã qua duyệt coi như ban hành & hiệu lực từ lúc tạo
UPDATE documents SET issued_date = created_at::date, effective_date = created_at::date
WHERE status IN ('ACTIVE', 'WARNING', 'EXPIRED');

-- index cho job quét APPROVED đến hạn hiệu lực hằng ngày
CREATE INDEX idx_documents_effective_date ON documents (effective_date) WHERE status = 'APPROVED';

-- audit log: EFFECTIVE (văn bản bắt đầu hiệu lực - tự động hoặc kích hoạt tay),
--            SET_EFFECTIVE (đổi ngày hiệu lực thủ công)
ALTER TABLE approval_requests DROP CONSTRAINT chk_approval_action;
ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_action
    CHECK (action IN ('CREATE', 'UPDATE', 'SUBMIT', 'APPROVE', 'REJECT', 'RENEW',
                      'REPLACE', 'REPEAL', 'AMEND', 'ADMIN_OVERRIDE',
                      'EFFECTIVE', 'SET_EFFECTIVE'));
