-- Bổ sung action ADMIN_OVERRIDE cho audit log (admin can thiệp văn bản).
ALTER TABLE approval_requests DROP CONSTRAINT chk_approval_action;
ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_action
    CHECK (action IN ('CREATE', 'UPDATE', 'SUBMIT', 'APPROVE', 'REJECT', 'RENEW',
                      'REPLACE', 'REPEAL', 'AMEND', 'ADMIN_OVERRIDE'));
