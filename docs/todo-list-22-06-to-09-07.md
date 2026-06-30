# TODO List theo ngày — 22/06 → 09/07/2026

> **Điểm bắt đầu:** Hạ tầng (Docker Compose, Nginx, `.env`) và codebase scaffold (4 service Spring Initializr + dependency) đã xong.
> **Phần còn lại:** Toàn bộ code thực — auth-service, document-service, notification-service, scheduler-service, **frontend (chưa `ng new`)**, dashboard, polish, test, báo cáo, demo.
> **Chiến lược:** Vertical slice (DB → API → UI) để luôn có thứ demo được. Bổ sung cho [ke-hoach-3-tuan-1-nguoi.md](./ke-hoach-3-tuan-1-nguoi.md).
> **Deadline:** 09/07 (Thứ 5).

---

## Tổng quan 18 ngày

| # | Ngày | Thứ | Trọng tâm | Milestone kiểm tra |
|---|------|-----|-----------|--------------------|
| 1 | 22/06 | T2 | auth-service: DB + entity + JWT | Flyway chạy, bảng users/departments có data |
| 2 | 23/06 | T3 | auth-service: API + security | `POST /api/auth/login` trả JWT đủ claims |
| 3 | 24/06 | T4 | auth-service dockerize + **CI/CD setup** (GitHub Actions) | Container auth chạy qua Nginx; CI pipeline green |
| 4 | 25/06 | T5 | document-service: DB + CRUD | `POST /api/documents` tạo DRAFT |
| 5 | 26/06 | T6 | document-service: approval workflow + outbox | Submit→PENDING, Approve→ACTIVE, OutboxRelayJob chạy |
| 6 | 27/06 | T7 | document-service: internal API + dockerize | `/internal/documents/expiring` trả dữ liệu đúng |
| 7 | 28/06 | CN | notification-service (full alert owner) | `POST /internal/trigger` → 30s → mail Mailhog |
| 8 | 29/06 | T2 | scheduler-service (thin proxy) + integration test E2E | `8084/trigger` → forward → mail đúng cấp |
| 9 | 30/06 | T3 | Backend buffer + CI/CD finalize | 4 service compose clean; CI badge xanh |
| 10 | 01/07 | T4 | Frontend: Angular scaffold + auth | `ng serve` + login E2E, guard theo role |
| 11 | 02/07 | T5 | Frontend: document list + form + detail | Tạo/xem/upload trên UI |
| 12 | 03/07 | T6 | Frontend: approval + alert log + admin | Duyệt/từ chối từ UI; alert log; user mgmt |
| 13 | 04/07 | T7 | Frontend: dashboard + gia hạn + polish | Stat cards, renew dialog, spinner/snackbar |
| 14 | 05/07 | CN | Frontend buffer + seed data | Fix bug UI, seed đủ scenarios test |
| 15 | 06/07 | T2 | E2E test + seed data + fix bug | Checklist test đầy đủ xanh |
| 16 | 07/07 | T3 | Báo cáo (kiến trúc + data model + feature) | Draft báo cáo ~80% |
| 17 | 08/07 | T4 | Slide + README + rehearsal demo | Demo chạy trơn |
| 18 | 09/07 | T5 | Buffer + **DEMO / NỘP BÀI** ✅ | |

---

## Tuần A — Backend hoàn chỉnh + CI/CD (22/06–30/06)

### ~~Ngày 1 — 22/06 (T2): auth-service DB + entity + JWT~~ ✅ DONE
- [x] Viết `V1__init_auth_schema.sql`:
  - Bảng `companies` (Công ty)
  - Bảng `departments` (Trung tâm) có `company_id` FK → `companies`
  - Bảng `users` có **4 roles**: `ADMIN`, `MANAGER_COMPANY`, `MANAGER_CENTER`, `USER`
  - `users.department_id` nullable (null với MANAGER_COMPANY và ADMIN)
  - `users.company_id` nullable (null với USER, MANAGER_CENTER, ADMIN)
  - Seed: 1 company, 3 departments, 1 ADMIN, 1 MANAGER_COMPANY, 1 MANAGER_CENTER
- [x] Cấu hình Flyway + JPA + JWT trong `application.yaml` (schema `auth_schema`, ddl-auto=validate)
- [x] Tạo entity `Company`, `Department`, `User` + enum `Role(ADMIN/MANAGER_COMPANY/MANAGER_CENTER/USER)`
- [x] Tạo `CompanyRepository`, `DepartmentRepository`, `UserRepository`
- [x] Implement `JwtUtil` — JWT claims bao gồm: `userId`, `role`, `departmentId` (nullable), `companyId` (nullable)
- [x] `docker-compose up postgres` → verify Flyway chạy, bảng + seed data có

### ~~Ngày 2 — 23/06 (T3): auth-service API + security~~ ✅ DONE
- [x] `JwtFilter` (OncePerRequestFilter) + `SecurityConfig` (permit `/login`, còn lại authenticated)
- [x] DTO: `LoginRequest`, `LoginResponse` (có thêm `companyId`), `RegisterRequest`, `UserDto`
- [x] `AuthController`: `POST /login`, `POST /register` (ADMIN)
- [x] `UserController`: `GET /users`, `PUT /users/{id}` (ADMIN)
- [x] `DepartmentController`: `GET/POST /departments` (Trung tâm)
- [x] `CompanyController`: `GET/POST /companies` (ADMIN)
- [x] Internal endpoints cho notification-service:
  - `GET /internal/users/{id}`
  - `GET /internal/manager/center/{deptId}` → tìm MANAGER_CENTER của Trung tâm đó
  - `GET /internal/manager/company/{companyId}` → tìm MANAGER_COMPANY của Công ty đó
  - `GET /internal/admin` → lấy email ADMIN
- [x] Test Postman: login → decode jwt.io → verify có `userId`, `role`, `departmentId` (nullable), `companyId` (nullable)

### ~~Ngày 3 — 24/06 (T4): auth-service dockerize + CI/CD setup~~ ✅ DONE
- [x] Viết `Dockerfile` multi-stage cho auth-service + `.dockerignore`
- [x] `docker-compose up --build auth-service` chạy ổn, login qua Nginx `http://localhost/api/auth/login`
- [x] Tạo `.github/workflows/ci.yml` — pipeline GitHub Actions — Task 1.CI
  - **Job 1 `build-test`:** checkout → Java 21 → `mvn package -DskipTests -B` cho cả 4 service
  - **Job 2 `docker-build`:** `docker compose build` (verify Dockerfile syntax + layer cache)
- [x] Push lên GitHub → xem tab Actions → 2 jobs green ✅
- [x] Thêm CI status badge vào `README.md`
- [x] **Verify:** CI green; auth-service accessible tại `http://localhost/api/auth/login`

### ~~Ngày 4 — 25/06 (T5): document-service DB + CRUD~~ ✅ DONE
- [x] `V1__init_document_schema.sql`:
  - Bảng `documents` với `type` (4 loại), `level` (`CENTER`/`COMPANY`/`GROUP`), `company_id`, `renewal_count`
  - Bảng `approval_requests`
  - Index trên `expiry_date`, `status`, `owner_id`, `department_id`, `company_id`, `level`
- [x] Cấu hình Flyway/JPA schema `document_schema` + `application.yaml`
- [x] Entity `Document`, `ApprovalRequest` + enum `DocumentStatus`, `DocumentType`, `DocumentLevel`
- [x] `JwtFilter` — parse thêm `companyId` từ JWT claims — Task 2.2
- [x] CRUD `POST/GET/GET{id}/PUT/DELETE /documents` với filter theo role:
  - `USER`: `owner_id = userId`
  - `MANAGER_CENTER`: `department_id = departmentId`
  - `MANAGER_COMPANY`: `company_id = companyId` (join qua departments hoặc lưu trực tiếp)
  - `ADMIN`: no filter
- [x] **Verify:** tạo văn bản với các level khác nhau, kiểm tra filter đúng role

### ~~Ngày 5 — 26/06 (T6): document-service approval workflow + upload + outbox~~ ✅ DONE
- [x] `DocumentService`: submit / approve / reject / renew — Task 2.3
  - **Approve gate theo level:** CENTER→MGR_CENTER+; COMPANY→MGR_COMPANY+; GROUP→ADMIN only (kèm scope: cùng dept/company)
  - **Self-approval:** `reviewerId == doc.ownerId` → ForbiddenException (mọi role)
  - Mỗi method đánh `@Transactional`, viết `notification_outbox` trong cùng transaction
  - **`renew()`**: WARNING/EXPIRED/ACTIVE → ACTIVE, `renewal_count++`, ghi log `RENEW`
- [x] Endpoint `POST /documents/{id}/renew` (body: `{newExpiryDate}`, validate `@Future`) — Task 2.3
- [x] Lưu `approval_requests` log mỗi hành động (SUBMIT / APPROVE / REJECT / RENEW)
- [x] Entity `NotificationOutbox` + `NotificationOutboxRepository` (+ migration V2, payload JSONB qua `@JdbcTypeCode`)
- [x] `OutboxRelayJob` — `@Scheduled(fixedDelay=10_000)` gọi `POST /internal/emails` sang notification-service (RestTemplate + `@EnableScheduling`) — Task 2.3b
- [x] **Verify:** approve → `notification_outbox` có entry PENDING → relay gửi → SENT; tắt notification-service → approve vẫn thành công, entry tồn tại trong DB chờ → retry 3 lần → FAILED ✓ (test thực tế)
- [x] `POST /documents/{id}/upload` validate PDF/Word, lưu vào volume `/app/uploads` (`file_path=/uploads/...` khớp nginx, multipart 10MB) — Task 2.4
- [x] `GlobalExceptionHandler` (BusinessException, Forbidden, NotFound) — Task 3.4 (đã làm từ Ngày 4)
- [x] **Verify:** Submit→PENDING, Approve→ACTIVE, Reject→REJECTED, Renew→ACTIVE; upload trả file_path — E2E test PASSED (create→submit→approve→renew→upload + negative cases)

### ~~Ngày 6 — 27/06 (T7): document-service internal API + dockerize~~ ✅ DONE
- [x] `AuthClient` (RestTemplate) gọi auth-service `/internal/users/{id}` → lấy email owner (nuốt lỗi trả null để không chặn cả batch); config `auth.service.url`
- [x] `GET /internal/documents/expiring?withinDays=30` — `InternalDocumentController` + `DocumentService.findExpiring` + query `findExpiring(statuses, threshold)`; trả {docId, level, daysLeft, departmentId, companyId, ownerEmail}, cache email theo ownerId — Task 2.5
- [x] `PATCH /internal/documents/{id}/status` — chỉ cho đặt WARNING/EXPIRED và chỉ từ ACTIVE/WARNING (validate); notification-service gọi khi EXPIRED/WARNING
- [x] Dockerize document-service (`EXPOSE 8082`); `docker compose up --build postgres auth document` chạy 3 service cùng nhau OK
- [x] **Verify:** E2E tạo→submit→approve (ACTIVE) → `/internal/documents/expiring` trả `daysLeft=10` + `ownerEmail` lấy chéo auth-service đúng; PATCH WARNING/EXPIRED 200, negative {DRAFT} → 400; 3 service (postgres healthy, auth, document) up cùng nhau, log không ERROR
- [ ] *(Hoãn sang Ngày 7)* approve → outbox relay → **mail Mailhog**: chờ notification-service có endpoint `/internal/emails`

### Ngày 7 — 28/06 (CN): notification-service (full alert owner)
- [ ] `V1__init_notification_schema.sql`: `alert_configs` (thêm `document_level`), `alert_queue`, `alert_logs` (unique index) — Task 2.6
- [ ] `EmailService` (HTML email, màu theo ngưỡng, JavaMailSender → Mailhog) — Task 2.7
- [ ] `AlertService.processAlert` — check trùng, gửi, log SENT/FAILED — Task 2.8
- [ ] `AlertQueueProcessor` — `@Scheduled(fixedDelay=30_000)`, đọc `alert_queue`, xử lý async + PATCH EXPIRED/WARNING sang document-service — Task 2.8b
- [ ] `DocumentClient` (Feign) → `GET /internal/documents/expiring` — Task 2.8c
- [ ] `AuthClient` (Feign) → `/internal/manager/center/{id}`, `/internal/manager/company/{id}`, `/internal/admin` — Task 2.8c
- [ ] `AlertSchedulingService` — `@Scheduled(cron="0 0 8 * * *")` tự pull DocumentClient + ghi `alert_queue` trong cùng DB (true outbox), `resolveRecipients(level × daysLeft)` — Task 2.8c
- [ ] `POST /internal/trigger` (InternalTriggerController → gọi `AlertSchedulingService.runCheck()`)
- [ ] `POST /internal/emails` (OutboxRelayJob của document-service gọi để gửi approval email)
- [ ] `GET /api/notifications/alert-logs` (filter phòng / khoảng ngày)
- [ ] `GET /api/notifications/dashboard/stats` — trả `{active, warning, expired, pending, expiringIn30Days[]}` filter theo role — Task 3.1
- [ ] **Verify:** POST `/internal/trigger` → `alert_queue` có entry → 30s sau mail trong Mailhog; gửi 2 lần → không trùng; dashboard stats trả đúng số theo role

### Ngày 8 — 29/06 (T2): scheduler-service (thin proxy) + integration test E2E
- [ ] `NotificationClient` (Feign, 1 method): `POST /internal/trigger` → notification-service
- [ ] `TriggerController`: `POST /internal/trigger` → forward sang notification-service
- [ ] `application.yml` scheduler: chỉ cần `services.notification.url`, không cần datasource/Flyway
- [ ] Dockerize scheduler-service (tất cả 4 service + postgres + mailhog lên cùng nhau)
- [ ] **Integration test E2E:**
  - `POST http://localhost:8084/internal/trigger` → forward → notification `runCheck()` → `alert_queue` entry → 30s → mail Mailhog
- [ ] **Verify:** văn bản CENTER T-7 → MANAGER_CENTER nhận mail; hết hạn → EXPIRED + ADMIN nhận mail; gửi 2 lần → không trùng

### Ngày 9 — 30/06 (T3): Backend buffer + CI/CD finalize
- [ ] Cập nhật CI job `build-test`: chạy `mvn test` thay vì `-DskipTests` (sau khi có một số test)
- [ ] Thêm `healthcheck` trong `docker-compose.yml` cho postgres + các service
- [ ] Review log toàn bộ 4 service: không có `ERROR` nào không xử lý
- [ ] Seed data test hoàn chỉnh: users 4 role, văn bản ở mọi ngưỡng (T-30/15/7/1/EXPIRED, level CENTER/COMPANY/GROUP)
- [ ] Dọn nợ kỹ thuật: exception handler còn thiếu, HTTP status code chưa đúng, nullable check
- [ ] **Verify cuối tuần A:** `docker-compose up --build` sạch → login 4 role → tạo + approve + alert; CI pipeline green

---

## Tuần B — Frontend (01/07–07/07)

### Ngày 10 — 01/07 (T4): Frontend scaffold + auth
- [ ] `ng new frontend --routing --style=scss --skip-tests` + `ng add @angular/material`
- [ ] `AuthService`: login/logout/getToken/hasRole/isLoggedIn
- [ ] `authInterceptor`: gắn Bearer token, handle 401 → logout
- [ ] `authGuard` + `roleGuard`
- [ ] Login component (reactive form + gọi API + lưu token)
- [ ] Layout cơ bản (toolbar + sidenav) + routing theo role
- [ ] **Verify:** 3 role login redirect đúng trang; guard chặn `/documents` khi chưa login

### Ngày 11 — 02/07 (T5): Frontend documents (list + form + detail + upload)
- [ ] Document list: bảng + filter status + màu chip theo trạng thái
- [ ] Document form (tạo/sửa) reactive form + validator ngày hết hạn tương lai
- [ ] Document detail: hiển thị đầy đủ + link file + upload PDF/Word
- [ ] Filter theo role (USER chỉ thấy của mình, MANAGER_CENTER thấy cả phòng)
- [ ] **Verify:** tạo / sửa / xem theo role; upload file hiện preview link

### Ngày 12 — 03/07 (T6): Frontend approval + alert log + admin
- [ ] Nút Submit / Approve / Reject theo role; Reject dialog (nhập lý do)
- [ ] Alert log page: bảng + filter phòng/ngày + màu SENT/FAILED
- [ ] Admin: user list + tạo user + đổi role + alert config ngưỡng remind_days
- [ ] **Verify:** User nộp duyệt → Manager duyệt từ UI → email Mailhog; admin tạo user + chỉnh ngưỡng

### Ngày 13 — 04/07 (T7): Frontend dashboard + gia hạn + polish
- [ ] Dashboard component: gọi `GET /api/notifications/dashboard/stats` → 4 stat cards + bảng sắp hết hạn 30 ngày — Task 3.2
- [ ] Renew dialog (chọn ngày mới, validate tương lai, gọi `POST /documents/{id}/renew`)
- [ ] Loading spinner + empty state + Snackbar error/success thống nhất
- [ ] **Verify:** dashboard số liệu khớp dữ liệu thật; renew WARNING/EXPIRED → ACTIVE → renewal_count tăng

### Ngày 14 — 05/07 (CN): Frontend buffer + seed data hoàn chỉnh
- [ ] Fix bug UI còn tồn đọng từ Days 10-13
- [ ] Kiểm tra cross-role edge case: USER không thấy nút Approve; MANAGER không duyệt văn bản của mình
- [ ] Seed data hoàn chỉnh: users 4 role, văn bản ở mọi trạng thái + level cho E2E test
- [ ] **Verify UI flow đầy đủ:** tạo → submit → approve → alert email → renew (không dùng Postman)

---

## Tuần C — Test, Báo cáo, Demo (06–09/07)

### Ngày 15 — 06/07 (T2): E2E test + fix bug
- [ ] Seed data test (users + văn bản các ngưỡng T-30/15/7/1/EXPIRED) — Task 3.7
- [ ] Chạy full checklist nghiệp vụ: tạo → nộp → duyệt/từ chối → sửa lại → gia hạn — Task 3.8
- [ ] Test cron: trigger → email đúng cấp, không trùng, EXPIRED cập nhật
- [ ] Test phân quyền: Manager không thấy phòng khác, User chỉ thấy của mình, Admin thấy hết
- [ ] Fix toàn bộ bug phát hiện được

### Ngày 16 — 07/07 (T3): Báo cáo
- [ ] Giới thiệu bài toán + mục tiêu — Task 3.9
- [ ] Kiến trúc microservices + sơ đồ triển khai Docker
- [ ] Mô hình dữ liệu: ERD 3 schema + giải thích không FK chéo schema
- [ ] Tính năng đã implement: approval, cảnh báo phân cấp, CRUD, upload
- [ ] Hướng dẫn cài đặt + kết luận/roadmap

### Ngày 17 — 08/07 (T4): Slide + README + rehearsal
- [ ] Slide demo theo flow chuẩn — Task 3.10
- [ ] `README.md`: 3 lệnh chạy được (clone → cd → `docker-compose up --build`)
- [ ] Dọn endpoint `/internal/trigger` nếu cần / ghi chú rõ
- [ ] **Rehearsal demo:** chạy `docker-compose up --build` sạch, đi hết flow 8 bước

### Ngày 18 — 09/07 (T5): Buffer + DEMO / NỘP BÀI ✅
- [ ] Final checklist — Task 3.11 (không exception trong log, Mailhog đúng nội dung, không lỗi console JS)
- [ ] Fix phút chót
- [ ] **DEMO + NỘP BÀI**

---

## Lưu ý quan trọng

- **Đường găng (critical path):** auth-service (N1–3) chặn mọi thứ → ưu tiên xong sớm. Backend hoàn chỉnh N1–9 trước, frontend N10–14 vibe code sau.
- **Frontend vibe code:** N10–13 tập trung happy path, không cần hoàn hảo; N14 là buffer fix bug UI.
- **CI/CD:** N3 setup pipeline (compile + docker build), N9 upgrade lên chạy test thật.
- **Nếu trễ tiến độ — cắt theo thứ tự:** (giữ) Login/JWT, CRUD, Approval, Cron email, CI pipeline → (giảm) Admin config ngưỡng → (bỏ trước) upload file, renew dialog, UI polish.
- **Ngày đệm thực tế:** N9 (backend buffer), N14 (frontend buffer), N18 (final buffer).
- **Test sớm liên tục:** mỗi service xong là Postman + Mailhog ngay, đừng dồn cuối.
