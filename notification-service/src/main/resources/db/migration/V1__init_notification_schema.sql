-- ============================================================
-- alert_configs: ngưỡng cảnh báo theo cấp văn bản (admin chỉnh ở FE Ngày 12)
-- alert_queue:   true-outbox, runCheck() ghi PENDING, processor xử lý
-- alert_logs:    nhật ký gửi, unique partial index chống gửi trùng/ngày
-- ============================================================
CREATE TABLE alert_configs (
    id             BIGSERIAL    PRIMARY KEY,
    document_level VARCHAR(20)  NOT NULL,            -- CENTER | COMPANY | GROUP
    warning_days   INTEGER      NOT NULL DEFAULT 30, -- daysLeft <= warning_days → WARNING
    escalate_days  INTEGER      NOT NULL DEFAULT 7,  -- daysLeft <= escalate_days → leo thang quản lý
    enabled        BOOLEAN      NOT NULL DEFAULT true,
    CONSTRAINT uq_alert_config_level UNIQUE (document_level)
);

CREATE TABLE alert_queue (
    id              BIGSERIAL    PRIMARY KEY,
    document_id     BIGINT       NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    recipient_role  VARCHAR(30)  NOT NULL,           -- OWNER | MANAGER_CENTER | MANAGER_COMPANY | ADMIN
    document_level  VARCHAR(20)  NOT NULL,
    department_id   BIGINT,
    company_id      BIGINT,
    days_left       INTEGER      NOT NULL,
    alert_type      VARCHAR(20)  NOT NULL,           -- WARNING | EXPIRED
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    processed_at    TIMESTAMP,
    CONSTRAINT chk_alert_queue_status CHECK (status IN ('PENDING','PROCESSED','FAILED'))
);
CREATE INDEX idx_alert_queue_pending ON alert_queue (created_at) WHERE status = 'PENDING';

CREATE TABLE alert_logs (
    id              BIGSERIAL    PRIMARY KEY,
    document_id     BIGINT       NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    recipient_role  VARCHAR(30),
    department_id   BIGINT,
    alert_type      VARCHAR(20)  NOT NULL,
    days_left       INTEGER,
    status          VARCHAR(20)  NOT NULL,           -- SENT | FAILED
    error_message   TEXT,
    sent_date       DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
-- chỉ SENT mới chống trùng → FAILED vẫn cho retry trong ngày
CREATE UNIQUE INDEX uq_alert_log_sent
    ON alert_logs (document_id, recipient_email, alert_type, sent_date)
    WHERE status = 'SENT';

INSERT INTO alert_configs (document_level, warning_days, escalate_days) VALUES
    ('CENTER', 30, 7), ('COMPANY', 30, 7), ('GROUP', 30, 7);