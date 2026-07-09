# Thiết kế: Gửi email cảnh báo hết hạn qua Gmail thật

- **Ngày:** 2026-07-09
- **Trạng thái:** Đã duyệt thiết kế (chờ review spec)
- **Phạm vi:** notification-service (backend) + cấu hình docker-compose/.env
- **Nguyên tắc bao trùm:** KHÔNG làm hỏng luồng gửi mail hiện có. Đây chủ yếu là thay đổi **cấu hình** + 3 điểm code nhỏ. Toàn bộ luồng enqueue → `AlertQueueProcessor` → `EmailService` giữ nguyên.

## 1. Mục tiêu

Cho phép notification-service gửi email cảnh báo văn bản sắp/đã hết hạn (và email duyệt) tới **hòm thư thật** thông qua **Gmail SMTP**, thay vì chỉ tới Mailhog test. Đồng thời:

- Giữ Mailhog cho môi trường dev/local (không lỡ gửi mail thật khi test).
- Có **safe-mode** để demo an toàn: mọi mail dồn về một địa chỉ test, không bounce/spam người dùng thật.
- Có **auto-retry** cho lỗi SMTP tạm thời (SMTP thật fail theo kiểu Mailhog không gặp).

## 2. Quyết định thiết kế (chốt với user)

1. **Provider: Gmail SMTP + App Password** (`smtp.gmail.com:587`, STARTTLS). Đủ cho demo (~500 mail/ngày), không cần domain riêng.
2. **Tách môi trường bằng Spring profile `prod`** (`application-prod.yaml`) — KHÔNG dùng env-var thuần. Lý do: Gmail cần bật đồng thời 4 flag (`host`/`port`/`auth`/`starttls`); gom vào một khối nguyên tử trong yaml prod → chỉ cần nhớ **một việc** là bật profile, loại bỏ footgun "quên STARTTLS → gửi plaintext". Config prod không bí mật nằm trong git (review được); chỉ secret đi qua env.
3. **Safe-mode redirect:** biến `ALERT_REDIRECT_TO`. Khi có giá trị, MỌI mail (bất kể recipient gốc) gửi về địa chỉ đó, giữ recipient gốc trong subject. Bật khi demo, để trống khi production thật.
4. **Auto-retry:** thêm cột `retry_count`. Khi gửi fail và chưa quá `max-retries` (mặc định 3) → **giữ item ở `PENDING`**, processor 30s sau tự thử lại (không backoff lũy tiến). Quá số lần → `FAILED` như hiện tại.
5. **Sender có display name:** `From` hiển thị `"VDT Hệ thống văn bản" <địa-chỉ-gmail>`. Lưu ý Gmail rewrite phần địa chỉ về tài khoản đã auth nhưng **giữ display name**.

## 3. Cấu hình (Spring profile)

### 3.1. `application.yaml` (default = dev, giữ Mailhog)

Giữ nguyên trỏ Mailhog để dev chạy không cần làm gì. Bổ sung 2 khoá `alert`:

```yaml
spring:
  mail:
    host: ${SPRING_MAIL_HOST:localhost}
    port: ${SPRING_MAIL_PORT:1025}
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
alert:
  from: no-reply@vdt.local
  from-name: ${ALERT_FROM_NAME:VDT Hệ thống văn bản}
  redirect-to: ${ALERT_REDIRECT_TO:}     # rỗng ở dev
  max-retries: ${ALERT_MAX_RETRIES:3}
```

### 3.2. `application-prod.yaml` (mới — khối Gmail cố định)

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${SPRING_MAIL_USERNAME}      # secret → env
    password: ${SPRING_MAIL_PASSWORD}      # App Password 16 ký tự → env
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
alert:
  from: ${ALERT_FROM}                       # = chính địa chỉ Gmail (Gmail rewrite From)
  redirect-to: ${ALERT_REDIRECT_TO:}        # set khi demo, rỗng khi chạy thật
```

Kích hoạt bằng `SPRING_PROFILES_ACTIVE=prod`.

### 3.3. `docker-compose.prod.yml`

Thêm cho service `notification-service`:

```yaml
notification-service:
  environment:
    SPRING_PROFILES_ACTIVE: prod
    SPRING_MAIL_USERNAME: ${SMTP_USERNAME}
    SPRING_MAIL_PASSWORD: ${SMTP_PASSWORD}
    ALERT_FROM: ${SMTP_USERNAME}
    ALERT_REDIRECT_TO: ${ALERT_REDIRECT_TO}
```

Các secret (`SMTP_USERNAME`, `SMTP_PASSWORD`, `ALERT_REDIRECT_TO`) đặt trong `.env` (đã gitignore). Bổ sung `.env.example` để tài liệu hoá tên biến (không chứa giá trị thật).

## 4. Thay đổi code

### 4.1. `EmailService.java` — display name + redirect

- Constructor inject thêm `@Value("${alert.from-name:}")` và `@Value("${alert.redirect-to:}")`.
- Trong `send(...)`:
  - **Display name:** nếu `fromName` không rỗng → `helper.setFrom(from, fromName)` (dùng overload có personal name, `UTF-8`); ngược lại `helper.setFrom(from)`.
  - **Redirect:** nếu `redirectTo` không rỗng:
    ```java
    subject = "[→ " + to + "] " + subject;
    to = redirectTo;
    ```
- Đặt ở `send()` private nên **cả mail cảnh báo (`sendExpiryAlert`) lẫn mail duyệt (`sendApproval`) đều được bảo vệ** — không phải sửa hai chỗ.

### 4.2. `AlertQueue.java` + migration — cột `retry_count`

- Migration mới `V3__alert_queue_retry_count.sql`:
  ```sql
  ALTER TABLE alert_queue ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
  ```
- Entity thêm field:
  ```java
  @Builder.Default @Column(name = "retry_count", nullable = false) private int retryCount = 0;
  ```

### 4.3. `AlertService.java` — logic retry

Trong `processAlert(...)`, inject `@Value("${alert.max-retries:3}")` và sửa nhánh catch:

```java
} catch (Exception e) {
    if (q.getRetryCount() + 1 < maxRetries) {
        q.setRetryCount(q.getRetryCount() + 1);
        q.setStatus("PENDING");          // giữ PENDING → processor thử lại sau 30s
        log.warn("Gửi mail thất bại (lần {}), sẽ thử lại: {}", q.getRetryCount(), e.getMessage());
        // KHÔNG ghi alert_log ở lần thử trung gian
    } else {
        saveLog(q, "FAILED", e.getMessage());
        q.setStatus("FAILED");
    }
}
q.setProcessedAt(LocalDateTime.now());
```

- Nhánh thành công (`SENT`) và dedup (`existsBy...SENT`) giữ nguyên — dedup không chặn retry vì log trung gian không ghi `SENT`.
- Item giữ `PENDING` với `created_at` cũ → `findTop50ByStatusOrderByCreatedAt("PENDING")` tự chọn lại ở vòng sau (FIFO). Không cần đổi query hay `AlertQueueProcessor`.
- `resend(logId)` thủ công của admin giữ nguyên làm fallback cuối. Item re-enqueue có `retry_count = 0` (mặc định) → được thử lại đầy đủ.

## 5. Kiểm thử & rollout

Công cụ đã có sẵn: `POST /api/notifications/admin/test` (gửi mail mẫu) và `POST /api/notifications/admin/run-check` (quét cảnh báo ngay).

Thứ tự an toàn:
1. Tạo App Password trong Google Account (bật 2FA trước).
2. Điền `.env`: `SMTP_USERNAME`, `SMTP_PASSWORD`, `ALERT_REDIRECT_TO` = email test của bạn.
3. Khởi động với profile prod (`docker-compose -f docker-compose.yml -f docker-compose.prod.yml up`).
4. Gọi `/api/notifications/admin/test` → xác nhận mail vào **inbox thật**, subject có tiền tố `[→ ...]`, From hiển thị display name.
5. Gọi `/api/notifications/admin/run-check` → test luồng cảnh báo thật end-to-end (vẫn redirect về email test).
6. Kiểm thử retry: tạm để sai `SMTP_PASSWORD` → xác nhận item ở lại `PENDING` và `retry_count` tăng qua vài vòng, cuối cùng `FAILED`.
7. Khi production thật: bỏ `ALERT_REDIRECT_TO` (để trống) → mail gửi tới người dùng thật.

## 6. Danh sách file tác động

| File | Thay đổi |
|---|---|
| `notification-service/src/main/resources/application.yaml` | thêm `alert.from-name`, `redirect-to`, `max-retries` |
| `notification-service/src/main/resources/application-prod.yaml` | **mới** — khối Gmail (host/port/auth/starttls/timeouts) |
| `notification-service/.../service/EmailService.java` | display name + redirect trong `send()` |
| `notification-service/.../service/AlertService.java` | logic retry trong `processAlert()` |
| `notification-service/.../entity/AlertQueue.java` | thêm field `retryCount` |
| `notification-service/src/main/resources/db/migration/V3__alert_queue_retry_count.sql` | **mới** — cột `retry_count` |
| `docker-compose.prod.yml` | env prod cho notification-service |
| `.env.example` | **mới/cập nhật** — tài liệu hoá biến SMTP (không giá trị thật) |

**Không đụng:** `AlertQueueProcessor`, luồng enqueue (`AlertSchedulingService`), controller, `SecurityConfig` → rủi ro thấp, dễ rollback.

## 7. Rủi ro & lưu ý

- **App Password bắt buộc bật 2FA** trên tài khoản Google; mật khẩu đăng nhập thường không dùng được cho SMTP.
- **Giới hạn Gmail** ~500 mail/ngày — đủ demo, không dùng cho tải thật.
- **Footgun profile:** nếu quên `SPRING_PROFILES_ACTIVE=prod`, app rơi về default Mailhog → mail "gửi ok" nhưng không tới ai. Ghi rõ trong README/checklist rollout.
- **Deliverability:** mail từ Gmail cá nhân có thể vào Spam ở một số nhà cung cấp; chấp nhận được cho mini-project.
