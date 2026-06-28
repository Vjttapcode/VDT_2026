-- ============================================================
-- document_schema — documents, approval_requests
-- Owned by document-service. KHÔNG FK chéo sang auth_schema.
-- owner_id / department_id / company_id chỉ lưu giá trị (logical ref).
-- ============================================================

-- ---- documents --------------------------------------------
-- type : 4 loại (CONTRACT, LICENSE, CERTIFICATE, SR)
-- level: CENTER | COMPANY | GROUP  (ai được duyệt)
-- status lifecycle: DRAFT -> PENDING -> ACTIVE -> WARNING -> EXPIRED
--                                    \-> REJECTED
CREATE TABLE documents (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    type            VARCHAR(30)  NOT NULL,
    level           VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    owner_id        BIGINT       NOT NULL,
    department_id   BIGINT,
    company_id      BIGINT,
    expiry_date     DATE         NOT NULL,
    file_path       VARCHAR(500),
    renewal_count   INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_doc_type
        CHECK (type IN ('CONTRACT', 'LICENSE', 'CERTIFICATE', 'SR')),
    CONSTRAINT chk_doc_level
        CHECK (level IN ('CENTER', 'COMPANY', 'GROUP')),
    CONSTRAINT chk_doc_status
        CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'WARNING', 'EXPIRED', 'REJECTED'))
);

CREATE INDEX idx_documents_expiry_date   ON documents (expiry_date);
CREATE INDEX idx_documents_status        ON documents (status);
CREATE INDEX idx_documents_owner_id      ON documents (owner_id);
CREATE INDEX idx_documents_department_id ON documents (department_id);
CREATE INDEX idx_documents_company_id    ON documents (company_id);
CREATE INDEX idx_documents_level         ON documents (level);

-- ---- approval_requests (audit log mọi hành động) -----------
-- action: SUBMIT | APPROVE | REJECT | RENEW
-- Lịch sử gia hạn xem qua các bản ghi action='RENEW'.
CREATE TABLE approval_requests (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT       NOT NULL REFERENCES documents (id),
    action        VARCHAR(20)  NOT NULL,
    requester_id  BIGINT,
    reviewer_id   BIGINT,
    comment       TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_approval_action
        CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'RENEW'))
);

CREATE INDEX idx_approval_document_id ON approval_requests (document_id);