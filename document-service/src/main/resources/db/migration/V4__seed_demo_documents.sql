-- ============================================================
-- V4 — Bổ sung văn bản DRAFT/PENDING/REJECTED để demo đủ vòng đời trên UI.
-- V3 chỉ seed ACTIVE nên UI thiếu data cho nút Gửi duyệt/Phê duyệt/Từ chối/Xóa.
-- owner 6/7/8 = USER dept 1/2/3 (auth V3). KHÔNG FK chéo schema.
-- ============================================================
SET search_path TO document_schema;

INSERT INTO documents (title, type, level, status, owner_id, department_id, company_id, expiry_date) VALUES
    -- DRAFT: demo nút "Gửi duyệt" + "Xóa" trong drawer
    ('HĐ thuê máy chủ (nháp)',           'CONTRACT',    'CENTER',  'DRAFT',    6, 1, 1, CURRENT_DATE + 120),
    ('SR nâng cấp mạng (nháp)',          'SR',          'CENTER',  'DRAFT',    7, 2, 1, CURRENT_DATE + 90),
    -- PENDING: demo Phê duyệt/Từ chối theo đúng cấp duyệt
    ('Giấy phép PCCC (chờ duyệt)',       'LICENSE',     'CENTER',  'PENDING',  6, 1, 1, CURRENT_DATE + 180),
    ('HĐ bảo hiểm (chờ duyệt)',          'CONTRACT',    'COMPANY', 'PENDING',  7, 2, 1, CURRENT_DATE + 200),
    ('Chứng nhận ISO (chờ duyệt)',       'CERTIFICATE', 'GROUP',   'PENDING',  8, 3, 1, CURRENT_DATE + 365),
    -- REJECTED: demo sửa lại + gửi duyệt lại
    ('SR mua sắm thiết bị (bị từ chối)', 'SR',          'CENTER',  'REJECTED', 6, 1, 1, CURRENT_DATE + 60);
