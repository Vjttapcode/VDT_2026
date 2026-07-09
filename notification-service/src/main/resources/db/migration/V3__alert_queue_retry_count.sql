-- ============================================================
-- Thêm bộ đếm retry cho alert_queue.
-- Gửi mail SMTP thật có thể fail tạm thời (timeout, rate limit):
-- fail -> giữ PENDING và tăng retry_count để processor thử lại sau 30s;
-- quá alert.max-retries (mặc định 3 lần thử) mới chuyển FAILED hẳn.
-- ============================================================
ALTER TABLE alert_queue ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
