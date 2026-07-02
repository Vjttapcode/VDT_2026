-- ============================================================
-- V3 — Seed thêm user để đủ 4 role + manager cho mọi trung tâm.
-- Mật khẩu mọi seed user = "password" (dùng hash đã sửa ở V2).
-- Bổ sung: MANAGER_CENTER cho dept 2/3 (escalation), USER cho dept 1/2/3.
-- Ràng buộc chk_users_org_scope: USER/MANAGER_CENTER -> department_id NOT NULL, company_id NULL.
-- ============================================================

SET search_path TO auth_schema;

INSERT INTO users (id, email, password_hash, full_name, role, department_id, company_id) VALUES
    (4, 'manager.center2@vdt.com',
        '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Trưởng Trung tâm Hạ tầng mạng', 'MANAGER_CENTER', 2, NULL),
    (5, 'manager.center3@vdt.com',
        '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Trưởng Trung tâm Kinh doanh', 'MANAGER_CENTER', 3, NULL),
    (6, 'user1@vdt.com',
        '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Nhân viên TT Phần mềm', 'USER', 1, NULL),
    (7, 'user2@vdt.com',
        '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Nhân viên TT Hạ tầng mạng', 'USER', 2, NULL),
    (8, 'user3@vdt.com',
        '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery',
        'Nhân viên TT Kinh doanh', 'USER', 3, NULL);

-- đồng bộ sequence sau khi insert id thủ công
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
