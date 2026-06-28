-- ============================================================
-- notification_outbox — Transactional Outbox Pattern
-- Ghi cùng transaction với việc đổi status văn bản.
-- OutboxRelayJob quét PENDING và đẩy sang notification-service.
-- ============================================================
CREATE TABLE notification_outbox (
    id          BIGSERIAL    PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,          -- APPROVAL_REQUEST | APPROVED | REJECTED
    document_id BIGINT       NOT NULL,
    payload     JSONB        NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    retry_count INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    sent_at     TIMESTAMP,
    CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- Partial index: relay job chỉ quét PENDING
CREATE INDEX idx_outbox_pending ON notification_outbox (created_at)
    WHERE status = 'PENDING';