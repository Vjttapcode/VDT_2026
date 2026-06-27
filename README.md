# VDT MiniProject — Hệ thống quản lý văn bản & cảnh báo hết hạn

![CI](https://github.com/Vjttapcode/VDT_2026/actions/workflows/ci.yml/badge.svg)

Hệ thống microservices quản lý văn bản, phê duyệt phân cấp và cảnh báo hết hạn qua email.

## Kiến trúc

- **auth-service** (8081) — xác thực JWT, quản lý user/company/department
- **document-service** (8082) — CRUD văn bản, workflow phê duyệt, outbox
- **notification-service** (8083) — gửi email cảnh báo phân cấp
- **scheduler-service** (8084) — trigger cron
- **nginx** (80) — API gateway · **postgres** (5432) · **mailhog** (8025)

## Chạy nhanh

```bash
git clone https://github.com/Vjttapcode/VDT_2026.git
cd VDT_2026
docker-compose up --build
```
