-- ============================================================
-- V3 — Seed văn bản test ở mọi ngưỡng cảnh báo × mọi level.
-- expiry_date tính tương đối theo CURRENT_DATE lúc migrate (T-30/15/7/1/EXPIRED).
-- owner_id/department_id/company_id là logical ref sang auth_schema
--   (owner 6/7/8 = USER dept 1/2/3 ở auth V3). KHÔNG FK chéo schema.
-- Seed status ACTIVE để luồng cron cảnh báo tự PATCH -> WARNING/EXPIRED.
-- ============================================================

SET search_path TO document_schema;

INSERT INTO documents (title, type, level, status, owner_id, department_id, company_id, expiry_date) VALUES
    -- CENTER (duyệt cấp Trung tâm) — đủ 5 ngưỡng
    ('HĐ dịch vụ TT-SW (T-30)',          'CONTRACT',    'CENTER',  'ACTIVE', 6, 1, 1, CURRENT_DATE + 30),
    ('Giấy phép TT-SW (T-15)',           'LICENSE',     'CENTER',  'ACTIVE', 6, 1, 1, CURRENT_DATE + 15),
    ('Chứng nhận TT-SW (T-7)',           'CERTIFICATE', 'CENTER',  'ACTIVE', 6, 1, 1, CURRENT_DATE + 7),
    ('SR TT-SW (T-1)',                   'SR',          'CENTER',  'ACTIVE', 6, 1, 1, CURRENT_DATE + 1),
    ('HĐ TT-SW (EXPIRED)',               'CONTRACT',    'CENTER',  'ACTIVE', 6, 1, 1, CURRENT_DATE - 3),
    -- COMPANY (leo thang cấp Công ty)
    ('HĐ cấp Công ty (T-7)',             'CONTRACT',    'COMPANY', 'ACTIVE', 7, 2, 1, CURRENT_DATE + 7),
    ('Giấy phép cấp Công ty (EXPIRED)',  'LICENSE',     'COMPANY', 'ACTIVE', 7, 2, 1, CURRENT_DATE - 2),
    -- GROUP (chỉ ADMIN duyệt — leo thang tới ADMIN)
    ('HĐ cấp Tập đoàn (T-15)',           'CONTRACT',    'GROUP',   'ACTIVE', 8, 3, 1, CURRENT_DATE + 15),
    ('SR cấp Tập đoàn (EXPIRED)',        'SR',          'GROUP',   'ACTIVE', 8, 3, 1, CURRENT_DATE - 5);
