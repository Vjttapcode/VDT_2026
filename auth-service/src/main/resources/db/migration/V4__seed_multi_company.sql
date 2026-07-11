-- ============================================================
-- V4 — Seed thêm 2 công ty × 2 trung tâm/công ty để demo/test đa công ty
-- (trước đây toàn bộ seed data chỉ có 1 công ty duy nhất, không đủ để
-- kiểm chứng cách ly dữ liệu chéo công ty của MANAGER_COMPANY/MANAGER_CENTER).
-- Mật khẩu mọi seed user = "password" (dùng hash đã sửa ở V2).
-- Ràng buộc chk_users_org_scope: USER/MANAGER_CENTER -> department_id NOT NULL, company_id NULL
--                                 MANAGER_COMPANY     -> department_id NULL,     company_id NOT NULL
--
-- KHÔNG hardcode id: môi trường đã chạy lâu (vd VPS) có thể đã tự sinh company/
-- department/user với id trùng số thủ công ở đây (qua CRUD Tổ chức trên UI) ->
-- insert cứng id sẽ vỡ khóa chính và làm cả service crash lúc khởi động.
-- Dùng code/email (đã UNIQUE) + subquery để BIGSERIAL tự cấp id, và
-- ON CONFLICT DO NOTHING để an toàn nếu vô tình chạy lại trên DB đã có sẵn.
-- ============================================================

SET search_path TO auth_schema;

INSERT INTO companies (name, code) VALUES
    ('Công ty Công nghệ ABC', 'ABC'),
    ('Công ty Đầu tư XYZ',    'XYZ')
ON CONFLICT (code) DO NOTHING;

INSERT INTO departments (name, code, company_id)
SELECT 'Trung tâm Phát triển ABC', 'ABC-DEV', c.id FROM companies c WHERE c.code = 'ABC'
UNION ALL
SELECT 'Trung tâm Vận hành ABC',   'ABC-OPS', c.id FROM companies c WHERE c.code = 'ABC'
UNION ALL
SELECT 'Trung tâm Tài chính XYZ',  'XYZ-FIN', c.id FROM companies c WHERE c.code = 'XYZ'
UNION ALL
SELECT 'Trung tâm Nhân sự XYZ',    'XYZ-HR',  c.id FROM companies c WHERE c.code = 'XYZ'
ON CONFLICT (code) DO NOTHING;

INSERT INTO users (email, password_hash, full_name, role, department_id, company_id)
SELECT 'manager.companyABC@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Trưởng Công ty ABC', 'MANAGER_COMPANY', NULL, c.id
FROM companies c WHERE c.code = 'ABC'
UNION ALL
SELECT 'manager.centerABC1@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Trưởng TT Phát triển ABC', 'MANAGER_CENTER', d.id, NULL
FROM departments d WHERE d.code = 'ABC-DEV'
UNION ALL
SELECT 'manager.centerABC2@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Trưởng TT Vận hành ABC', 'MANAGER_CENTER', d.id, NULL
FROM departments d WHERE d.code = 'ABC-OPS'
UNION ALL
SELECT 'userABC1@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Nhân viên TT Phát triển ABC', 'USER', d.id, NULL
FROM departments d WHERE d.code = 'ABC-DEV'
UNION ALL
SELECT 'userABC2@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Nhân viên TT Vận hành ABC', 'USER', d.id, NULL
FROM departments d WHERE d.code = 'ABC-OPS'
UNION ALL
SELECT 'manager.companyXYZ@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Trưởng Công ty XYZ', 'MANAGER_COMPANY', NULL, c.id
FROM companies c WHERE c.code = 'XYZ'
UNION ALL
SELECT 'manager.centerXYZ1@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Trưởng TT Tài chính XYZ', 'MANAGER_CENTER', d.id, NULL
FROM departments d WHERE d.code = 'XYZ-FIN'
UNION ALL
SELECT 'manager.centerXYZ2@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Trưởng TT Nhân sự XYZ', 'MANAGER_CENTER', d.id, NULL
FROM departments d WHERE d.code = 'XYZ-HR'
UNION ALL
SELECT 'userXYZ1@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Nhân viên TT Tài chính XYZ', 'USER', d.id, NULL
FROM departments d WHERE d.code = 'XYZ-FIN'
UNION ALL
SELECT 'userXYZ2@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
       'Nhân viên TT Nhân sự XYZ', 'USER', d.id, NULL
FROM departments d WHERE d.code = 'XYZ-HR'
ON CONFLICT (email) DO NOTHING;
