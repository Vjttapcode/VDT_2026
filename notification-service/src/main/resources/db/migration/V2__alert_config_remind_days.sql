-- ============================================================
-- Thêm mốc nhắc nhiều lần cho alert_configs.
-- remind_days: danh sách mốc (ngày trước hết hạn) sẽ gửi cảnh báo, vd "30,15,7,1".
-- Trước đây runCheck() gửi MỖI NGÀY trong 30 ngày -> spam. Giờ chỉ gửi đúng mốc.
-- ============================================================
ALTER TABLE alert_configs ADD COLUMN remind_days VARCHAR(100) NOT NULL DEFAULT '30,15,7,1';
