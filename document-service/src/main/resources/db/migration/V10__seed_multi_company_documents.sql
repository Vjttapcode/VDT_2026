-- ============================================================
-- V10 — Văn bản mẫu cho 2 công ty mới (ABC/XYZ, seed ở auth-service V4)
-- để demo/test phân quyền xem theo company/department qua nhiều tổ chức.
-- owner_id/department_id/company_id là logical ref sang auth_schema (KHÔNG FK chéo schema):
--   ABC: userABC1=23 (dept4/company2), userABC2=24 (dept5/company2)
--   XYZ: userXYZ1=28 (dept6/company3), userXYZ2=29 (dept7/company3)
-- COMPANY level: department_id NULL (đúng invariant hiện tại của resolveTarget()).
-- ============================================================
SET search_path TO document_schema;

INSERT INTO documents (title, type, level, status, owner_id, department_id, company_id, expiry_date) VALUES
    -- Công ty ABC — cấp Trung tâm
    ('HĐ dịch vụ TT Phát triển ABC (T-30)', 'CONTRACT',    'CENTER',  'ACTIVE', 23, 4, 2, CURRENT_DATE + 30),
    ('Giấy phép TT Phát triển ABC (T-7)',   'LICENSE',     'CENTER',  'ACTIVE', 23, 4, 2, CURRENT_DATE + 7),
    ('SR TT Vận hành ABC (EXPIRED)',        'SR',          'CENTER',  'ACTIVE', 24, 5, 2, CURRENT_DATE - 2),
    ('SR nâng cấp hệ thống ABC (nháp)',     'SR',          'CENTER',  'DRAFT',  24, 5, 2, CURRENT_DATE + 100),
    -- Công ty ABC — cấp Công ty
    ('HĐ cấp Công ty ABC (T-20)',           'CONTRACT',    'COMPANY', 'ACTIVE',  23, NULL, 2, CURRENT_DATE + 20),
    ('Giấy phép cấp Công ty ABC (chờ duyệt)','LICENSE',    'COMPANY', 'PENDING', 24, NULL, 2, CURRENT_DATE + 90),

    -- Công ty XYZ — cấp Trung tâm
    ('HĐ dịch vụ TT Tài chính XYZ (T-15)',  'CONTRACT',    'CENTER',  'ACTIVE', 28, 6, 3, CURRENT_DATE + 15),
    ('Chứng nhận TT Nhân sự XYZ (T-5)',     'CERTIFICATE', 'CENTER',  'ACTIVE', 29, 7, 3, CURRENT_DATE + 5),
    -- Công ty XYZ — cấp Công ty
    ('HĐ bảo hiểm cấp Công ty XYZ (T-60)',  'CONTRACT',    'COMPANY', 'ACTIVE',  28, NULL, 3, CURRENT_DATE + 60);
