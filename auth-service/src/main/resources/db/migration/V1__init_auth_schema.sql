-- ============================================================
-- auth_schema — companies, departments, users
-- Owned by auth-service. No cross-schema FK.
-- Org model 3 tầng: Tập đoàn (Group) > Công ty (Company) > Trung tâm (Department/Center)
-- ============================================================

-- ---- companies (Công ty) ----------------------------------
CREATE TABLE companies (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ---- departments (Trung tâm) ------------------------------
CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_departments_company_id ON departments (company_id);

-- ---- users -------------------------------------------------
-- 4 roles: ADMIN, MANAGER_COMPANY, MANAGER_CENTER, USER
-- Nullable rules:
--   USER / MANAGER_CENTER -> department_id NOT NULL, company_id NULL
--   MANAGER_COMPANY       -> company_id NOT NULL,    department_id NULL
--   ADMIN                 -> cả hai NULL
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(30)  NOT NULL,
    department_id   BIGINT       REFERENCES departments (id),
    company_id      BIGINT       REFERENCES companies (id),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'MANAGER_COMPANY', 'MANAGER_CENTER', 'USER')),
    -- ràng buộc nullable theo role
    CONSTRAINT chk_users_org_scope CHECK (
        (role = 'ADMIN'            AND department_id IS NULL     AND company_id IS NULL)
     OR (role = 'MANAGER_COMPANY'  AND department_id IS NULL     AND company_id IS NOT NULL)
     OR (role = 'MANAGER_CENTER'   AND department_id IS NOT NULL AND company_id IS NULL)
     OR (role = 'USER'             AND department_id IS NOT NULL AND company_id IS NULL)
    )
);

CREATE INDEX idx_users_department_id ON users (department_id);
CREATE INDEX idx_users_company_id    ON users (company_id);
CREATE INDEX idx_users_role          ON users (role);

-- ============================================================
-- Seed data
-- Password (BCrypt) cho mọi seed user: "password"
-- hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- ============================================================

-- 1 company
INSERT INTO companies (id, name, code) VALUES
    (1, 'Công ty Viễn thông số VDT', 'VDT');

-- 3 departments (đều thuộc company 1)
INSERT INTO departments (id, name, code, company_id) VALUES
    (1, 'Trung tâm Phát triển phần mềm', 'TT-SW',  1),
    (2, 'Trung tâm Hạ tầng mạng',        'TT-NET', 1),
    (3, 'Trung tâm Kinh doanh',          'TT-BIZ', 1);

-- Users: 1 ADMIN, 1 MANAGER_COMPANY, 1 MANAGER_CENTER
INSERT INTO users (email, password_hash, full_name, role, department_id, company_id) VALUES
    ('admin@vdt.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'Quản trị Tập đoàn', 'ADMIN', NULL, NULL),
    ('manager.company@vdt.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'Trưởng Công ty VDT', 'MANAGER_COMPANY', NULL, 1),
    ('manager.center@vdt.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'Trưởng Trung tâm Phần mềm', 'MANAGER_CENTER', 1, NULL);

-- đồng bộ sequence sau khi insert id thủ công
SELECT setval('companies_id_seq',   (SELECT MAX(id) FROM companies));
SELECT setval('departments_id_seq', (SELECT MAX(id) FROM departments));
SELECT setval('users_id_seq',       (SELECT MAX(id) FROM users));
