-- ============================================================
-- V11 — Sửa lại data từ V10: V10 gốc hardcode owner_id/department_id/
-- company_id là số literal (23, 4, 2, ...) copy từ 1 lần chạy thử cục bộ.
-- Trên môi trường thật, các ID đó KHÔNG khớp với id thật do auth-service
-- tự sinh (BIGSERIAL) -> để lại các dòng "rác", tham chiếu tới company/
-- department/user không hề tồn tại (documents không có FK chéo schema
-- nên không báo lỗi, chỉ âm thầm sai).
--
-- V10 đã được migrate + ghi checksum trên môi trường thật nên KHÔNG được
-- sửa nội dung nữa (Flyway sẽ báo "checksum mismatch" và app không khởi
-- động được — đây chính là lỗi vừa gặp). Migration mới luôn là cách đúng
-- để sửa dữ liệu do 1 migration cũ đã áp dụng gây ra.
--
-- Bước 1: xoá đúng 9 dòng rác V10 tạo ra (nhận diện qua title, seed-only,
-- không đụng dữ liệu người dùng thật).
-- Bước 2: seed lại bằng owner/department/company ID THẬT, tra qua
-- auth_schema bằng email/code (unique) — bọc trong EXECUTE + kiểm tra
-- schema tồn tại để an toàn cả khi chạy test cô lập (CI matrix, không có
-- auth_schema) lẫn môi trường thật (có auth_schema).
-- ============================================================
SET search_path TO document_schema;

DELETE FROM documents WHERE title IN (
    'HĐ dịch vụ TT Phát triển ABC (T-30)',
    'Giấy phép TT Phát triển ABC (T-7)',
    'SR TT Vận hành ABC (EXPIRED)',
    'SR nâng cấp hệ thống ABC (nháp)',
    'HĐ cấp Công ty ABC (T-20)',
    'Giấy phép cấp Công ty ABC (chờ duyệt)',
    'HĐ dịch vụ TT Tài chính XYZ (T-15)',
    'Chứng nhận TT Nhân sự XYZ (T-5)',
    'HĐ bảo hiểm cấp Công ty XYZ (T-60)'
);

DO $do$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'auth_schema') THEN
        EXECUTE $sql$
            INSERT INTO documents (title, type, level, status, owner_id, department_id, company_id, expiry_date)
            SELECT 'HĐ dịch vụ TT Phát triển ABC (T-30)', 'CONTRACT', 'CENTER', 'ACTIVE',
                   u.id, d.id, d.company_id, CURRENT_DATE + 30
            FROM auth_schema.users u, auth_schema.departments d
            WHERE u.email = 'userABC1@vdt.com' AND d.code = 'ABC-DEV'
            UNION ALL
            SELECT 'Giấy phép TT Phát triển ABC (T-7)', 'LICENSE', 'CENTER', 'ACTIVE',
                   u.id, d.id, d.company_id, CURRENT_DATE + 7
            FROM auth_schema.users u, auth_schema.departments d
            WHERE u.email = 'userABC1@vdt.com' AND d.code = 'ABC-DEV'
            UNION ALL
            SELECT 'SR TT Vận hành ABC (EXPIRED)', 'SR', 'CENTER', 'ACTIVE',
                   u.id, d.id, d.company_id, CURRENT_DATE - 2
            FROM auth_schema.users u, auth_schema.departments d
            WHERE u.email = 'userABC2@vdt.com' AND d.code = 'ABC-OPS'
            UNION ALL
            SELECT 'SR nâng cấp hệ thống ABC (nháp)', 'SR', 'CENTER', 'DRAFT',
                   u.id, d.id, d.company_id, CURRENT_DATE + 100
            FROM auth_schema.users u, auth_schema.departments d
            WHERE u.email = 'userABC2@vdt.com' AND d.code = 'ABC-OPS'
            UNION ALL
            SELECT 'HĐ cấp Công ty ABC (T-20)', 'CONTRACT', 'COMPANY', 'ACTIVE',
                   u.id, NULL, c.id, CURRENT_DATE + 20
            FROM auth_schema.users u, auth_schema.companies c
            WHERE u.email = 'userABC1@vdt.com' AND c.code = 'ABC'
            UNION ALL
            SELECT 'Giấy phép cấp Công ty ABC (chờ duyệt)', 'LICENSE', 'COMPANY', 'PENDING',
                   u.id, NULL, c.id, CURRENT_DATE + 90
            FROM auth_schema.users u, auth_schema.companies c
            WHERE u.email = 'userABC2@vdt.com' AND c.code = 'ABC'
            UNION ALL
            SELECT 'HĐ dịch vụ TT Tài chính XYZ (T-15)', 'CONTRACT', 'CENTER', 'ACTIVE',
                   u.id, d.id, d.company_id, CURRENT_DATE + 15
            FROM auth_schema.users u, auth_schema.departments d
            WHERE u.email = 'userXYZ1@vdt.com' AND d.code = 'XYZ-FIN'
            UNION ALL
            SELECT 'Chứng nhận TT Nhân sự XYZ (T-5)', 'CERTIFICATE', 'CENTER', 'ACTIVE',
                   u.id, d.id, d.company_id, CURRENT_DATE + 5
            FROM auth_schema.users u, auth_schema.departments d
            WHERE u.email = 'userXYZ2@vdt.com' AND d.code = 'XYZ-HR'
            UNION ALL
            SELECT 'HĐ bảo hiểm cấp Công ty XYZ (T-60)', 'CONTRACT', 'COMPANY', 'ACTIVE',
                   u.id, NULL, c.id, CURRENT_DATE + 60
            FROM auth_schema.users u, auth_schema.companies c
            WHERE u.email = 'userXYZ1@vdt.com' AND c.code = 'XYZ'
        $sql$;
    END IF;
END
$do$;
