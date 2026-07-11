-- ============================================================
-- V10 — Văn bản mẫu cho 2 công ty mới (ABC/XYZ, seed ở auth-service V4)
-- để demo/test phân quyền xem theo company/department qua nhiều tổ chức.
--
-- KHÔNG hardcode owner_id/department_id/company_id: auth-service V4 không còn
-- gán id cứng (tránh đụng id đã tồn tại trên DB đã chạy lâu), nên ở đây tra
-- ngược qua auth_schema bằng email/code (đã UNIQUE) để lấy đúng id thật.
--
-- Bọc trong DO block + EXECUTE (dynamic SQL): CI chạy mvn test theo matrix,
-- mỗi service 1 Postgres RIÊNG BIỆT — document-service test không có
-- auth_schema (auth-service không chạy trong job đó) -> câu SELECT chéo schema
-- sẽ lỗi "schema does not exist" nếu viết SQL tĩnh bình thường, làm
-- ApplicationContext (contextLoads) không load được -> build fail.
-- EXECUTE hoãn resolve tên bảng tới lúc thực thi, nên chỉ chạy khi auth_schema
-- thực sự tồn tại (deploy thật / test tay) — ngược lại bỏ qua êm, không lỗi.
-- COMPANY level: department_id NULL (đúng invariant hiện tại của resolveTarget()).
-- ============================================================
SET search_path TO document_schema;

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
