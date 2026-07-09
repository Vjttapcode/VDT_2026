# Gửi email cảnh báo qua Gmail thật — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho notification-service gửi email cảnh báo hết hạn (và email duyệt) tới hòm thư thật qua Gmail SMTP, với safe-mode redirect, auto-retry và display name sender; dev vẫn dùng Mailhog.

**Architecture:** Giữ nguyên luồng enqueue → `AlertQueueProcessor` (30s) → `AlertService.processAlert` → `EmailService`. Chỉ thêm: (1) profile `prod` chứa khối Gmail cố định, (2) redirect + display name bọc tại `EmailService.send()`, (3) cột `retry_count` + logic giữ `PENDING` khi fail chưa quá 3 lần.

**Tech Stack:** Spring Boot 3 (Java 17+), JavaMailSender (spring-boot-starter-mail), Flyway/PostgreSQL (schema `notification_schema`), JUnit 5 + Mockito (spring-boot-starter-test), Docker Compose.

**Spec:** `docs/superpowers/specs/2026-07-09-real-email-alerts-design.md`

## Global Constraints

- KHÔNG sửa `AlertQueueProcessor`, `AlertSchedulingService`, controller, `SecurityConfig` — luồng cũ giữ nguyên.
- Comment/code style tiếng Việt như codebase hiện có.
- Default (không profile) = Mailhog `localhost:1025`, auth/starttls tắt — dev chạy không cần cấu hình gì.
- Profile `prod` = Gmail `smtp.gmail.com:587`, `auth=true`, `starttls=true`, timeout 5000ms cả ba loại.
- `alert.max-retries` mặc định `3`; nghĩa là tối đa 3 lần thử (điều kiện `retryCount + 1 < maxRetries` ở nhánh catch).
- Retry KHÔNG ghi `alert_log` ở lần thử trung gian; chỉ ghi `FAILED` ở lần cuối, `SENT` khi thành công.
- Display name mặc định: `VDT Hệ thống văn bản`.
- Secret chỉ nằm trong `.env` (đã gitignore); `.env.example` chỉ chứa tên biến + placeholder.
- Lệnh test chạy từ `notification-service/`: `./mvnw test -Dtest=<TestClass>` (KHÔNG chạy `mvn test` trần — `contextLoads` cần DB).

---

### Task 1: Cột `retry_count` (migration V3 + entity)

**Files:**
- Create: `notification-service/src/main/resources/db/migration/V3__alert_queue_retry_count.sql`
- Modify: `notification-service/src/main/java/com/vdt/notification_service/entity/AlertQueue.java` (thêm 1 field sau `alertType`, dòng ~34)

**Interfaces:**
- Consumes: bảng `alert_queue` hiện có (V1), entity `AlertQueue` (Lombok `@Getter @Setter @Builder`).
- Produces: `AlertQueue.getRetryCount(): int` / `setRetryCount(int)` — Task 3 dùng.

- [x] **Step 1: Viết migration V3**

Tạo `notification-service/src/main/resources/db/migration/V3__alert_queue_retry_count.sql`:

```sql
-- ============================================================
-- Thêm bộ đếm retry cho alert_queue.
-- Gửi mail SMTP thật có thể fail tạm thời (timeout, rate limit):
-- fail -> giữ PENDING và tăng retry_count để processor thử lại sau 30s;
-- quá alert.max-retries (mặc định 3 lần thử) mới chuyển FAILED hẳn.
-- ============================================================
ALTER TABLE alert_queue ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
```

- [x] **Step 2: Thêm field vào entity `AlertQueue`**

Trong `AlertQueue.java`, ngay sau dòng `private String alertType;`, thêm:

```java
    @Builder.Default @Column(name = "retry_count", nullable = false)  private int retryCount = 0;
```

- [x] **Step 3: Verify compile**

Run (từ `notification-service/`): `./mvnw -q compile`
Expected: BUILD SUCCESS, không lỗi.

- [x] **Step 4: Commit**

```bash
git add notification-service/src/main/resources/db/migration/V3__alert_queue_retry_count.sql notification-service/src/main/java/com/vdt/notification_service/entity/AlertQueue.java
git commit -m "feat(notification): thêm cột retry_count vào alert_queue"
```

---

### Task 2: `EmailService` — display name + safe-mode redirect (TDD)

**Files:**
- Modify: `notification-service/src/main/java/com/vdt/notification_service/service/EmailService.java`
- Test: `notification-service/src/test/java/com/vdt/notification_service/service/EmailServiceTest.java` (mới)

**Interfaces:**
- Consumes: `JavaMailSender` (mock trong test).
- Produces: constructor mới `EmailService(JavaMailSender, String from, String fromName, String redirectTo)` — Spring inject qua `@Value`; hai method public `sendExpiryAlert(...)`, `sendApproval(...)` giữ nguyên signature.

- [x] **Step 1: Viết test fail**

Tạo `notification-service/src/test/java/com/vdt/notification_service/service/EmailServiceTest.java`:

```java
package com.vdt.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailServiceTest {

    private JavaMailSender mockSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        return sender;
    }

    private MimeMessage sent(JavaMailSender sender) {
        ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(cap.capture());
        return cap.getValue();
    }

    @Test
    void redirect_batMoiMailVeDiaChiTest_giuRecipientGocTrongSubject() throws Exception {
        JavaMailSender sender = mockSender();
        EmailService svc = new EmailService(sender, "bot@gmail.com", "VDT Hệ thống văn bản", "tester@gmail.com");

        svc.sendExpiryAlert("user@congty.vn", 42L, "COMPANY", 7, "WARNING");

        MimeMessage msg = sent(sender);
        assertThat(msg.getAllRecipients()).hasSize(1);
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("tester@gmail.com");
        assertThat(msg.getSubject()).startsWith("[→ user@congty.vn]");
    }

    @Test
    void khongRedirect_giuNguyenRecipientGoc() throws Exception {
        JavaMailSender sender = mockSender();
        EmailService svc = new EmailService(sender, "bot@gmail.com", "VDT Hệ thống văn bản", "");

        svc.sendExpiryAlert("user@congty.vn", 42L, "COMPANY", 7, "WARNING");

        MimeMessage msg = sent(sender);
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("user@congty.vn");
        assertThat(msg.getSubject()).doesNotContain("[→");
    }

    @Test
    void from_coDisplayName() throws Exception {
        JavaMailSender sender = mockSender();
        EmailService svc = new EmailService(sender, "bot@gmail.com", "VDT Hệ thống văn bản", "");

        svc.sendApproval("user@congty.vn", "APPROVED", 1L, "Quy chế A", null);

        InternetAddress from = (InternetAddress) sent(sender).getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("bot@gmail.com");
        assertThat(from.getPersonal()).isEqualTo("VDT Hệ thống văn bản");
    }

    @Test
    void from_khongCoDisplayName_khiFromNameRong() throws Exception {
        JavaMailSender sender = mockSender();
        EmailService svc = new EmailService(sender, "no-reply@vdt.local", "", "");

        svc.sendApproval("user@congty.vn", "APPROVED", 1L, "Quy chế A", null);

        InternetAddress from = (InternetAddress) sent(sender).getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@vdt.local");
        assertThat(from.getPersonal()).isNull();
    }
}
```

- [x] **Step 2: Chạy test, xác nhận FAIL**

Run: `./mvnw test -Dtest=EmailServiceTest`
Expected: COMPILE ERROR — constructor `EmailService(JavaMailSender, String, String, String)` chưa tồn tại (hiện chỉ có 2 tham số).

- [x] **Step 3: Sửa `EmailService.java`**

Thay toàn bộ constructor và hàm `send(...)` (giữ nguyên `sendExpiryAlert`, `sendApproval`):

```java
@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;
    private final String redirectTo;

    public EmailService(JavaMailSender mailSender,
            @Value("${alert.from:no-reply@vdt.local}") String from,
            @Value("${alert.from-name:}") String fromName,
            @Value("${alert.redirect-to:}") String redirectTo) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
        this.redirectTo = redirectTo;
    }
```

và:

```java
    private void send(String to, String subject, String html) {
        try {
            // Safe mode: dồn mọi mail về 1 địa chỉ test, giữ recipient gốc trong subject
            if (redirectTo != null && !redirectTo.isBlank()) {
                subject = "[→ " + to + "] " + subject;
                to = redirectTo;
            }
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            if (fromName != null && !fromName.isBlank()) h.setFrom(from, fromName);
            else h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Gửi mail thất bại tới " + to + ": " + e.getMessage(), e);
        }
    }
```

(`setFrom(String, String)` throws `UnsupportedEncodingException` nên catch thêm.)

- [x] **Step 4: Chạy test, xác nhận PASS**

Run: `./mvnw test -Dtest=EmailServiceTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0` — BUILD SUCCESS.

- [x] **Step 5: Commit**

```bash
git add notification-service/src/main/java/com/vdt/notification_service/service/EmailService.java notification-service/src/test/java/com/vdt/notification_service/service/EmailServiceTest.java
git commit -m "feat(notification): display name sender + safe-mode redirect trong EmailService"
```

---

### Task 3: `AlertService` — auto-retry giữ PENDING (TDD)

**Files:**
- Modify: `notification-service/src/main/java/com/vdt/notification_service/service/AlertService.java`
- Test: `notification-service/src/test/java/com/vdt/notification_service/service/AlertServiceTest.java` (mới)

**Interfaces:**
- Consumes: `AlertQueue.getRetryCount()/setRetryCount(int)` (Task 1); `EmailService.sendExpiryAlert(String, Long, String, long, String)` (mock).
- Produces: constructor mới `AlertService(EmailService, AlertLogRepository, AlertQueueRepository, int maxRetries)` — Spring inject `@Value("${alert.max-retries:3}")`.

- [x] **Step 1: Viết test fail**

Tạo `notification-service/src/test/java/com/vdt/notification_service/service/AlertServiceTest.java`:

```java
package com.vdt.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vdt.notification_service.entity.AlertLog;
import com.vdt.notification_service.entity.AlertQueue;
import com.vdt.notification_service.repository.AlertLogRepository;
import com.vdt.notification_service.repository.AlertQueueRepository;

class AlertServiceTest {

    private EmailService emailService;
    private AlertLogRepository logRepo;
    private AlertService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        logRepo = mock(AlertLogRepository.class);
        service = new AlertService(emailService, logRepo, mock(AlertQueueRepository.class), 3);
        // chưa gửi SENT hôm nay -> không bị dedup chặn
        when(logRepo.existsByDocumentIdAndRecipientEmailAndAlertTypeAndSentDateAndStatus(
                anyLong(), anyString(), anyString(), any(LocalDate.class), anyString()))
            .thenReturn(false);
    }

    private AlertQueue queueItem(int retryCount) {
        AlertQueue q = AlertQueue.builder().documentId(1L).recipientEmail("a@b.vn")
            .recipientRole("STAFF").documentLevel("COMPANY").daysLeft(7)
            .alertType("WARNING").retryCount(retryCount).build();
        return q;
    }

    @Test
    void guiFail_lanDau_giuPending_tangRetry_khongGhiLog() {
        doThrow(new RuntimeException("smtp timeout")).when(emailService)
            .sendExpiryAlert(anyString(), anyLong(), anyString(), anyLong(), anyString());
        AlertQueue q = queueItem(0);

        service.processAlert(q);

        assertThat(q.getStatus()).isEqualTo("PENDING");
        assertThat(q.getRetryCount()).isEqualTo(1);
        verify(logRepo, never()).save(any());
    }

    @Test
    void guiFail_qua3LanThu_chuyenFailed_ghiLogFailed() {
        doThrow(new RuntimeException("smtp down")).when(emailService)
            .sendExpiryAlert(anyString(), anyLong(), anyString(), anyLong(), anyString());
        AlertQueue q = queueItem(2); // lần thử thứ 3 (retryCount 2 + 1 = 3 = maxRetries)

        service.processAlert(q);

        assertThat(q.getStatus()).isEqualTo("FAILED");
        ArgumentCaptor<AlertLog> cap = ArgumentCaptor.forClass(AlertLog.class);
        verify(logRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(cap.getValue().getErrorMessage()).isEqualTo("smtp down");
    }

    @Test
    void guiThanhCong_processed_ghiLogSent() {
        AlertQueue q = queueItem(0);

        service.processAlert(q);

        assertThat(q.getStatus()).isEqualTo("PROCESSED");
        ArgumentCaptor<AlertLog> cap = ArgumentCaptor.forClass(AlertLog.class);
        verify(logRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("SENT");
    }
}
```

- [x] **Step 2: Chạy test, xác nhận FAIL**

Run: `./mvnw test -Dtest=AlertServiceTest`
Expected: COMPILE ERROR — constructor `AlertService(..., int)` chưa tồn tại.

- [x] **Step 3: Sửa `AlertService.java`**

Thêm import + logger + field `maxRetries`, sửa constructor và nhánh catch của `processAlert`:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
```

```java
@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private final EmailService emailService;
    private final AlertLogRepository alertLogRepository;
    private final AlertQueueRepository alertQueueRepository;
    private final int maxRetries;

    public AlertService(EmailService emailService, AlertLogRepository alertLogRepository,
            AlertQueueRepository alertQueueRepository,
            @Value("${alert.max-retries:3}") int maxRetries) {
        this.emailService = emailService;
        this.alertLogRepository = alertLogRepository;
        this.alertQueueRepository = alertQueueRepository;
        this.maxRetries = maxRetries;
    }
```

Nhánh catch trong `processAlert` đổi thành:

```java
        } catch (Exception e) {
            if (q.getRetryCount() + 1 < maxRetries) {
                // Lỗi tạm thời: giữ PENDING để processor 30s sau thử lại, không ghi log trung gian
                q.setRetryCount(q.getRetryCount() + 1);
                log.warn("Gửi mail cảnh báo doc #{} tới {} thất bại (lần thử {}), sẽ thử lại: {}",
                        q.getDocumentId(), q.getRecipientEmail(), q.getRetryCount(), e.getMessage());
            } else {
                saveLog(q, "FAILED", e.getMessage());
                q.setStatus("FAILED");
            }
        }
        q.setProcessedAt(LocalDateTime.now());
```

(Nhánh try/success và dedup đầu hàm giữ nguyên. Lưu ý: status khởi tạo đã là `PENDING` nên nhánh retry không cần `setStatus`.)

- [x] **Step 4: Chạy test, xác nhận PASS (cả 2 test class)**

Run: `./mvnw test -Dtest='EmailServiceTest,AlertServiceTest'`
Expected: `Tests run: 7, Failures: 0, Errors: 0` — BUILD SUCCESS.

- [x] **Step 5: Commit**

```bash
git add notification-service/src/main/java/com/vdt/notification_service/service/AlertService.java notification-service/src/test/java/com/vdt/notification_service/service/AlertServiceTest.java
git commit -m "feat(notification): auto-retry gửi mail — giữ PENDING tối đa 3 lần thử"
```

---

### Task 4: Cấu hình — `application.yaml` + `application-prod.yaml`

**Files:**
- Modify: `notification-service/src/main/resources/application.yaml` (khối `alert:`, dòng 45-46)
- Create: `notification-service/src/main/resources/application-prod.yaml`

**Interfaces:**
- Consumes: các `@Value` đã khai báo ở Task 2 (`alert.from-name`, `alert.redirect-to`) và Task 3 (`alert.max-retries`).
- Produces: profile `prod` kích hoạt bằng `SPRING_PROFILES_ACTIVE=prod` — Task 5 dùng.

- [x] **Step 1: Sửa khối `alert:` trong `application.yaml`**

Thay:

```yaml
alert:
  from: no-reply@vdt.local
```

bằng:

```yaml
alert:
  from: no-reply@vdt.local
  from-name: ${ALERT_FROM_NAME:VDT Hệ thống văn bản}
  redirect-to: ${ALERT_REDIRECT_TO:}     # rỗng ở dev (Mailhog nhận hết, không cần redirect)
  max-retries: ${ALERT_MAX_RETRIES:3}
```

- [x] **Step 2: Tạo `application-prod.yaml`**

Tạo `notification-service/src/main/resources/application-prod.yaml`:

```yaml
# ============================================================
# Profile prod: gửi email THẬT qua Gmail SMTP (App Password).
# Kích hoạt: SPRING_PROFILES_ACTIVE=prod
# 4 flag Gmail (host/port/auth/starttls) cố định thành khối nguyên tử
# để không thể quên lẻ một flag. Secret đi qua env (.env, không commit).
# Lưu ý: Gmail rewrite From = tài khoản đã auth -> ALERT_FROM phải là
# chính địa chỉ Gmail đăng nhập.
# ============================================================
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${SPRING_MAIL_USERNAME}
    password: ${SPRING_MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000

alert:
  from: ${ALERT_FROM}
  redirect-to: ${ALERT_REDIRECT_TO:}   # set khi demo (safe mode), rỗng khi chạy thật
```

- [x] **Step 3: Verify unit test vẫn xanh (yaml không phá context)**

Run: `./mvnw test -Dtest='EmailServiceTest,AlertServiceTest'`
Expected: `Tests run: 7, Failures: 0` — BUILD SUCCESS.

- [x] **Step 4: Commit**

```bash
git add notification-service/src/main/resources/application.yaml notification-service/src/main/resources/application-prod.yaml
git commit -m "feat(notification): profile prod cho Gmail SMTP + cấu hình alert redirect/retry"
```

---

### Task 5: Docker Compose prod + `.env.example`

**Files:**
- Modify: `docker-compose.prod.yml` (khối `notification-service`, dòng 10-11)
- Create: `.env.example`

**Interfaces:**
- Consumes: profile `prod` (Task 4).
- Produces: biến `.env`: `SMTP_USERNAME`, `SMTP_PASSWORD`, `ALERT_REDIRECT_TO`.

- [x] **Step 1: Sửa `docker-compose.prod.yml`**

Thay khối:

```yaml
  notification-service:
    ports: !reset []
```

bằng:

```yaml
  notification-service:
    ports: !reset []
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_MAIL_USERNAME: ${SMTP_USERNAME}
      SPRING_MAIL_PASSWORD: ${SMTP_PASSWORD}
      ALERT_FROM: ${SMTP_USERNAME} # Gmail rewrite From về tài khoản auth
      ALERT_REDIRECT_TO: ${ALERT_REDIRECT_TO:-} # set = email test khi demo, rỗng khi chạy thật
```

(Compose merge map `environment` với file base — các biến `SPRING_MAIL_HOST/PORT` của base vẫn còn nhưng bị profile prod trong yaml đè, vô hại.)

- [x] **Step 2: Tạo `.env.example`**

Tạo `.env.example` ở repo root (chỉ placeholder, KHÔNG giá trị thật):

```bash
# Database
POSTGRES_DB=vdt_db
POSTGRES_USER=vdt
POSTGRES_PASSWORD=change-me

# JWT
JWT_SECRET=your-256-bit-secret-key-here-minimum-32-chars
JWT_EXPIRATION=86400000

# Gmail SMTP (chỉ dùng với docker-compose.prod.yml / SPRING_PROFILES_ACTIVE=prod)
# SMTP_PASSWORD = App Password 16 ký tự (Google Account -> Security -> 2-Step Verification -> App passwords)
SMTP_USERNAME=your-account@gmail.com
SMTP_PASSWORD=xxxx xxxx xxxx xxxx
# Safe mode demo: mọi mail dồn về địa chỉ này (recipient gốc giữ trong subject). Để trống khi chạy thật.
ALERT_REDIRECT_TO=your-test-inbox@gmail.com
```

- [x] **Step 3: Verify compose config hợp lệ**

Run (từ repo root): `docker compose -f docker-compose.yml -f docker-compose.prod.yml config --quiet && echo OK`
Expected: `OK` (chỉ warning biến chưa set nếu `.env` thiếu biến SMTP — chấp nhận được).

- [x] **Step 4: Commit**

```bash
git add docker-compose.prod.yml .env.example
git commit -m "feat(deploy): cấu hình Gmail SMTP cho notification-service ở compose prod"
```

---

### Task 6: Verification end-to-end (dev mode — Flyway V3 + context thật)

**Files:** không sửa file — chỉ chạy kiểm chứng.

- [x] **Step 1: Chạy toàn bộ unit test mới**

Run (từ `notification-service/`): `./mvnw test -Dtest='EmailServiceTest,AlertServiceTest'`
Expected: `Tests run: 7, Failures: 0, Errors: 0`.

- [x] **Step 2: Build image + khởi động notification-service (dev, Mailhog)**

Run (từ repo root): `docker compose up -d --build notification-service`
Expected: container start thành công.

- [x] **Step 3: Xác nhận Flyway áp migration V3 + app UP**

Run: `docker compose logs notification-service | grep -iE "flyway|migrat|Started"`
Expected: thấy `Migrating schema "notification_schema" to version "3 - alert queue retry count"` (hoặc `Successfully applied 1 migration`) và `Started NotificationServiceApplication`.

- [x] **Step 4: Smoke test gửi mail dev qua Mailhog**

Login lấy JWT admin rồi gọi `POST /api/notifications/admin/test` (qua nginx `http://localhost/api/notifications/admin/test` hoặc port 8083). Mở Mailhog UI `http://localhost:8025` — thấy mail test với From hiển thị `VDT Hệ thống văn bản`.

- [x] **Step 5: Commit plan checkboxes + kết thúc**

```bash
git add docs/superpowers/plans/2026-07-09-real-email-alerts.md
git commit -m "docs: hoàn tất plan gửi email cảnh báo qua Gmail thật"
```

**Rollout Gmail thật (thủ công, ngoài phạm vi code — theo spec mục 5):** bật 2FA → tạo App Password → điền `.env` (`SMTP_USERNAME`, `SMTP_PASSWORD`, `ALERT_REDIRECT_TO`=email test) → `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build` → gọi `/api/notifications/admin/test` xác nhận vào inbox thật → `/run-check` test luồng cảnh báo → khi chạy thật bỏ `ALERT_REDIRECT_TO`.
