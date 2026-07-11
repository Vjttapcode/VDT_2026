-- ============================================================
-- V4 — Seed thêm 2 công ty × 2 trung tâm/công ty để demo/test đa công ty
-- (trước đây toàn bộ seed data chỉ có 1 công ty duy nhất, không đủ để
-- kiểm chứng cách ly dữ liệu chéo công ty của MANAGER_COMPANY/MANAGER_CENTER).
-- Mật khẩu mọi seed user = "password" (dùng hash đã sửa ở V2).
-- Ràng buộc chk_users_org_scope: USER/MANAGER_CENTER -> department_id NOT NULL, company_id NULL
--                                 MANAGER_COMPANY     -> department_id NULL,     company_id NOT NULL
-- ============================================================

SET search_path TO auth_schema;

INSERT INTO companies (id, name, code) VALUES
    (2, 'Công ty Công nghệ ABC', 'ABC'),
    (3, 'Công ty Đầu tư XYZ',    'XYZ');

INSERT INTO departments (id, name, code, company_id) VALUES
    (4, 'Trung tâm Phát triển ABC', 'ABC-DEV', 2),
    (5, 'Trung tâm Vận hành ABC',   'ABC-OPS', 2),
    (6, 'Trung tâm Tài chính XYZ',  'XYZ-FIN', 3),
    (7, 'Trung tâm Nhân sự XYZ',    'XYZ-HR',  3);

INSERT INTO users (id, email, password_hash, full_name, role, department_id, company_id) VALUES
    (20, 'manager.companyABC@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Trưởng Công ty ABC', 'MANAGER_COMPANY', NULL, 2),
    (21, 'manager.centerABC1@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Trưởng TT Phát triển ABC', 'MANAGER_CENTER', 4, NULL),
    (22, 'manager.centerABC2@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Trưởng TT Vận hành ABC', 'MANAGER_CENTER', 5, NULL),
    (23, 'userABC1@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Nhân viên TT Phát triển ABC', 'USER', 4, NULL),
    (24, 'userABC2@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Nhân viên TT Vận hành ABC', 'USER', 5, NULL),
    (25, 'manager.companyXYZ@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Trưởng Công ty XYZ', 'MANAGER_COMPANY', NULL, 3),
    (26, 'manager.centerXYZ1@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Trưởng TT Tài chính XYZ', 'MANAGER_CENTER', 6, NULL),
    (27, 'manager.centerXYZ2@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Trưởng TT Nhân sự XYZ', 'MANAGER_CENTER', 7, NULL),
    (28, 'userXYZ1@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Nhân viên TT Tài chính XYZ', 'USER', 6, NULL),
    (29, 'userXYZ2@vdt.com', '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Nhân viên TT Nhân sự XYZ', 'USER', 7, NULL);

-- đồng bộ sequence sau khi insert id thủ công
SELECT setval('companies_id_seq',   (SELECT MAX(id) FROM companies));
SELECT setval('departments_id_seq', (SELECT MAX(id) FROM departments));
SELECT setval('users_id_seq',       (SELECT MAX(id) FROM users));
