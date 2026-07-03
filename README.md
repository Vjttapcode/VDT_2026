# VDT MiniProject — Hệ thống quản lý văn bản & cảnh báo hết hạn

![CI](https://github.com/Vjttapcode/VDT_2026/actions/workflows/ci.yml/badge.svg)

Hệ thống microservices quản lý văn bản, phê duyệt phân cấp và cảnh báo hết hạn qua email. Giao diện Angular theo thiết kế đỏ trắng, đầy đủ dashboard, quản lý văn bản, nhật ký cảnh báo và trang quản trị.

## Kiến trúc

- **frontend** (Angular 20 + nginx) — dashboard, văn bản, cảnh báo, lịch hết hạn, nhật ký, quản trị
- **auth-service** (8081) — xác thực JWT, quản lý user/company/department
- **document-service** (8082) — CRUD văn bản, workflow phê duyệt, outbox
- **notification-service** (8083) — gửi email cảnh báo phân cấp
- **scheduler-service** (8084) — trigger cron
- **nginx** (80) — gateway: serve UI tại `/` + proxy `/api/*` · **postgres** (5432) · **mailhog** (8025)

## Chạy nhanh

```bash
git clone https://github.com/Vjttapcode/VDT_2026.git
cd VDT_2026
docker-compose up --build
```

Sau khi các container healthy:

| Địa chỉ | Nội dung |
|---|---|
| http://localhost | Giao diện web (bản production) |
| http://localhost:8025 | Mailhog — hộp thư nhận email cảnh báo |
| `POST http://localhost:8084/internal/trigger` | Chạy kiểm tra cảnh báo thủ công (thay cho chờ cron 8h sáng) |

## Tài khoản demo

Mật khẩu chung: `password`

| Email | Vai trò |
|---|---|
| `admin@vdt.com` | Quản trị Tập đoàn — thấy tất cả, duyệt cấp GROUP, trang Quản trị |
| `manager.company@vdt.com` | Trưởng Công ty — duyệt cấp COMPANY |
| `manager.center@vdt.com` | Trưởng Trung tâm Phần mềm — duyệt cấp CENTER |
| `user1@vdt.com` | Nhân viên TT Phần mềm — chỉ thấy văn bản của mình |

## Phát triển frontend (dev)

```bash
# backend chạy Docker, frontend chạy dev server hot-reload
docker-compose up -d
cd frontend
npm install
npx ng serve        # http://localhost:4200, proxy API theo proxy.conf.json
```

## Luồng demo gợi ý

1. Login `user1` → "+ Thêm văn bản" (chọn "Gửi duyệt ngay") → logout
2. Login `manager.center` → mở văn bản chờ duyệt → Phê duyệt → Mailhog có mail `[Đã duyệt]`
3. `curl -X POST localhost:8084/internal/trigger` → chờ 30 giây → Mailhog có mail cảnh báo, trang "Nhật ký cảnh báo" có bản ghi mới
4. Văn bản hết hạn → mở drawer → "Gia hạn +6 tháng"
5. Login `admin` → trang Quản trị: tạo user, đổi vai trò, chỉnh ngưỡng cảnh báo
