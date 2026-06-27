-- ============================================================
-- V2 — Sửa BCrypt hash của seed users về đúng mật khẩu "password".
-- Hash trong V1 ($2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy)
-- KHÔNG khớp "password" (verify bằng BCryptPasswordEncoder.matches -> false).
-- Không sửa trực tiếp V1 để tránh vỡ Flyway checksum trên DB đã migrate.
-- ============================================================

SET search_path TO auth_schema;

UPDATE users
SET password_hash = '$2a$10$wf2dI.2yD6KPogeqA3yd3OlfGerADRbGD3DfYIK0n0w4FYgS3Bery'
WHERE email IN (
    'admin@vdt.com',
    'manager.company@vdt.com',
    'manager.center@vdt.com'
);
