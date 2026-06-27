# auth-service — API & Verify (Ngày 2, 23/06)

Service xác thực: cấp JWT, quản lý user/company/department, internal endpoints cho các service khác.

- **Port trực tiếp:** `8081`
- **Qua Nginx:** prefix `/api/auth/` → strip về root (vd `POST /api/auth/login` → `POST /login`)
- **JWT claims:** `sub` (email), `userId`, `role`, `departmentId` (nullable), `companyId` (nullable)

## Tài khoản seed

> Mật khẩu BCrypt của mọi seed user là `password`.

| Email | Role | departmentId | companyId |
|-------|------|:---:|:---:|
| `admin@vdt.com` | `ADMIN` | null | null |
| `manager.company@vdt.com` | `MANAGER_COMPANY` | null | 1 |
| `manager.center@vdt.com` | `MANAGER_CENTER` | 1 | null |

Tổ chức seed: Công ty `VDT` (id 1) · 3 Trung tâm: `TT-SW` (1), `TT-NET` (2), `TT-BIZ` (3).

## Endpoints

| Method | Path | Quyền | Body / Ghi chú |
|--------|------|-------|----------------|
| POST | `/login` | Public | `{ email, password }` → `LoginResponse` (token + claims) |
| POST | `/register` | ADMIN | `{ email, password, fullName, role, departmentId?, companyId? }` |
| GET | `/users` | ADMIN | Danh sách user |
| PUT | `/users/{id}` | ADMIN | `{ fullName, isActive }` |
| GET | `/departments` | ADMIN, MANAGER_COMPANY, MANAGER_CENTER | Danh sách Trung tâm |
| POST | `/departments` | ADMIN | `{ name, code, companyId }` |
| GET | `/companies` | ADMIN | Danh sách Công ty |
| POST | `/companies` | ADMIN | `{ name, code }` |
| GET | `/internal/users/{id}` | Internal (no token) | Thông tin user |
| GET | `/internal/manager/center/{deptId}` | Internal | MANAGER_CENTER của Trung tâm |
| GET | `/internal/manager/company/{companyId}` | Internal | MANAGER_COMPANY của Công ty |
| GET | `/internal/admin` | Internal | Email ADMIN |

> Ràng buộc `role × org scope` (khớp CHECK trong DB): `ADMIN` → dept & company đều null; `MANAGER_COMPANY` → companyId, dept null; `MANAGER_CENTER`/`USER` → departmentId, company null.

## Chạy & smoke test

```bash
# 1. Bật Postgres (chọn 1):
docker compose up -d postgres          # khuyến nghị — container PG15
# 2. Chạy service:
cd auth-service && ./mvnw spring-boot:run
```

Smoke test bằng curl (PowerShell dùng `Invoke-RestMethod` cũng được):

```bash
# Login admin -> lấy token
TOKEN=$(curl -s -X POST http://localhost:8081/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@vdt.com","password":"password"}' | jq -r .token)

curl -s http://localhost:8081/users -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8081/internal/admin
curl -s http://localhost:8081/internal/manager/center/1
```

Hoặc import `docs/auth-service.postman_collection.json` vào Postman, chạy **Login (admin)** trước (token tự lưu), rồi chạy các request còn lại.

## Checklist verify Ngày 2

- [ ] `POST /login` (admin) trả `token`; dán vào [jwt.io](https://jwt.io) thấy `userId`, `role=ADMIN`, `departmentId=null`, `companyId=null`.
- [ ] `POST /login` (manager.center) → `departmentId=1`, `companyId=null`.
- [ ] `POST /login` sai mật khẩu → 400, không lộ chi tiết.
- [ ] `GET /users` với token ADMIN → 200; với token MANAGER → 403.
- [ ] `POST /register` (ADMIN) tạo USER `departmentId=1` → 201; login USER mới thấy claims đúng.
- [ ] `GET /departments` với MANAGER_CENTER → 200.
- [ ] `GET /internal/admin` → `admin@vdt.com`; `GET /internal/manager/center/1` → `manager.center@vdt.com`.
- [ ] Không request nào ném 500 ngoài ý muốn (xem log service).
