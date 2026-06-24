# Hệ thống Quản lý & Cảnh báo Hết hạn Văn bản / SR

> **Phiên bản:** 1.3 — Draft (Miniproject Sinh viên — Microservices)
> **Ngày:** 2026-06-15 | **Cập nhật:** 2026-06-23
> **Tác giả:** *(điền tên nhóm)*
> **Thời gian thực hiện:** 3 tuần
> **Trạng thái:** Đang soạn thảo

---

## Changelog

| Version | Ngày | Thay đổi |
|---|---|---|
| **1.6** | 23/06/2026 | Chuyển cron logic vào notification-service (Option B): notification-service tự pull document-service + ghi `alert_queue` trong cùng schema → true outbox; scheduler-service thu gọn thành thin trigger proxy, không còn business logic |
| 1.5 | 23/06/2026 | Áp dụng Transactional Outbox Pattern: (1) `notification_outbox` trong document_schema — document-service viết cùng transaction khi đổi status, `OutboxRelayJob` @Scheduled(10s) relay sang notification-service; (2) `alert_queue` trong notification_schema — scheduler gọi REST nhanh (202), `AlertQueueProcessor` @Scheduled(30s) xử lý async; cập nhật Section 7, 8, ke-hoach, todo-list |
| 1.4 | 23/06/2026 | Thêm phân cấp tổ chức 3 tầng (Trung tâm < Công ty < Tập đoàn): mở rộng từ 3 role → 4 role (`USER`, `MANAGER_CENTER`, `MANAGER_COMPANY`, `ADMIN`); thêm field `level` vào documents (`CENTER`/`COMPANY`/`GROUP`); thêm bảng `companies`; cập nhật ma trận phân quyền, luồng alert, và data model theo 3 cấp |
| 1.3 | 23/06/2026 | Xử lý góp ý reviewer: Thu hẹp loại văn bản còn 4 loại; self-approval rule; `renewal_count`; ghi chú kiến trúc shared-DB; xác nhận ngưỡng max 30 ngày |
| 1.2 | 15/06/2026 | Draft ban đầu — phân rã 4 service, schema, API list |

---

## Mục lục

1. [Tổng quan nghiệp vụ](#1-tổng-quan-nghiệp-vụ)
2. [Phạm vi đề tài](#2-phạm-vi-đề-tài)
3. [Phân quyền & Vai trò](#3-phân-quyền--vai-trò)
4. [Luồng nghiệp vụ chính](#4-luồng-nghiệp-vụ-chính)
5. [Kiến trúc hệ thống](#5-kiến-trúc-hệ-thống)
6. [Bảng công nghệ](#6-bảng-công-nghệ)
7. [Mô hình dữ liệu](#7-mô-hình-dữ-liệu)
8. [Luồng cảnh báo tự động](#8-luồng-cảnh-báo-tự-động)
9. [Vòng đời trạng thái văn bản](#9-vòng-đời-trạng-thái-văn-bản)
10. [Khả năng mở rộng & tích hợp](#10-khả-năng-mở-rộng--tích-hợp)
11. [Kế hoạch 3 tuần](#11-kế-hoạch-3-tuần)
12. [Danh sách API](#12-danh-sách-api)

---

## 1. Tổng quan nghiệp vụ

| Khía cạnh | Mô tả |
|---|---|
| **Mục tiêu** | Quản lý danh sách văn bản / SR có ngày hết hạn; tự động gửi email cảnh báo trước khi hết hạn theo từng cấp |
| **Đối tượng sử dụng** | Nhân viên (USER) · Trưởng Trung tâm (MANAGER_CENTER) · Trưởng Công ty (MANAGER_COMPANY) · Quản trị Tập đoàn (ADMIN) |
| **Cấp văn bản** | **3 cấp:** Trung tâm (CENTER) · Công ty (COMPANY) · Tập đoàn (GROUP) |
| **Loại văn bản** | **4 loại:** Hợp đồng (CONTRACT) · Giấy phép (LICENSE) · Chứng chỉ (CERTIFICATE) · SR nội bộ (SR) |
| **Quy trình duyệt** | Nhân viên tạo → Manager cấp tương ứng duyệt → Kích hoạt theo dõi hết hạn. **Không tự duyệt văn bản của mình.** |
| **Quy tắc cảnh báo** | T-30/15 → User; T-7 → User + Manager cấp trực tiếp; T-1 → User + Manager cấp trực tiếp + cấp trên. **Max ngưỡng: 30 ngày** |
| **Kiến trúc** | Microservices cơ bản: 4 service độc lập, giao tiếp REST, triển khai Docker Compose |
| **Kênh thông báo** | Email (SMTP) — kênh duy nhất trong phạm vi đề tài |
| **Hành động sau cảnh báo** | Gia hạn, đánh dấu đã xử lý, hoặc hệ thống tự chuyển EXPIRED |

---

## 2. Phạm vi đề tài

### Trong phạm vi (In-scope)

| # | Tính năng |
|---|---|
| 1 | **Microservices**: 4 service riêng biệt — auth, document, notification, scheduler |
| 2 | **API Gateway** (Nginx): định tuyến request, forward JWT đến đúng service |
| 3 | Đăng ký / Đăng nhập, phân quyền **4 vai trò**: USER, MANAGER_CENTER, MANAGER_COMPANY, ADMIN |
| 4 | CRUD văn bản / SR: tạo, xem, sửa, xóa, lọc theo trạng thái — **4 loại**: CONTRACT, LICENSE, CERTIFICATE, SR; **3 cấp**: CENTER, COMPANY, GROUP |
| 5 | **Luồng duyệt theo cấp**: cấp nào của văn bản thì Manager cấp đó duyệt; không tự duyệt văn bản mình tạo |
| 6 | Upload file đính kèm (PDF / Word, lưu local disk) |
| 7 | Cài đặt ngưỡng cảnh báo theo loại văn bản (Admin) — **max 30 ngày** |
| 8 | Cron job hàng ngày: quét và gửi email **theo từng cấp** (T-30/15/7/1) |
| 9 | Dashboard: thống kê tổng hợp, danh sách sắp hết hạn (lọc theo phòng) |
| 10 | Lịch sử cảnh báo đã gửi (alert log) |
| 11 | Gia hạn văn bản — cập nhật tại chỗ + ghi log `RENEW` vào `approval_requests` |

### Ngoài phạm vi (Out-of-scope) — Hướng phát triển tiếp theo

| Tính năng | Lý do loại khỏi scope |
|---|---|
| SMS / Push notification | Phức tạp, tốn phí API |
| Tích hợp ERP / DMS / JIRA | Không có môi trường test |
| SSO / Active Directory | Overkill cho demo sinh viên |
| Mobile app | Cần thêm 2–3 tuần riêng |
| Message queue (Kafka/RabbitMQ) | Thay bằng REST đồng bộ cho đơn giản |
| Service mesh / Distributed tracing | Quá phức tạp cho 3 tuần |
| Luồng duyệt 3+ cấp | Có thể mở rộng sau khi hoàn thành luồng 2 cấp |

---

## 3. Phân quyền & Vai trò

### 3.1 Cấu trúc tổ chức 3 tầng

```mermaid
graph TD
    TĐ["🏢 TẬP ĐOÀN\n(Group)"]
    CT1["🏭 Công ty A\n(Company)"]
    CT2["🏭 Công ty B\n(Company)"]
    TT1["🏬 Trung tâm A1"]
    TT2["🏬 Trung tâm A2"]
    TT3["🏬 Trung tâm B1"]
    NV["👤 Nhân viên\n(Users)"]

    TĐ --> CT1
    TĐ --> CT2
    CT1 --> TT1
    CT1 --> TT2
    CT2 --> TT3
    TT1 & TT2 & TT3 --> NV

    style TĐ fill:#b71c1c,color:#fff
    style CT1 fill:#e65100,color:#fff
    style CT2 fill:#e65100,color:#fff
    style TT1 fill:#1565c0,color:#fff
    style TT2 fill:#1565c0,color:#fff
    style TT3 fill:#1565c0,color:#fff
    style NV fill:#2e7d32,color:#fff
```

### 3.2 Mô tả các vai trò

```mermaid
graph TD
    ADMIN["ADMIN\nQuản trị Tập đoàn"]
    MC["MANAGER_COMPANY\nTrưởng Công ty"]
    MCC["MANAGER_CENTER\nTrưởng Trung tâm"]
    USER["USER\nNhân viên"]

    ADMIN -->|Kế thừa tất cả| MC
    MC -->|Kế thừa quyền Trung tâm| MCC
    MCC -->|Kế thừa quyền cơ bản| USER

    style ADMIN fill:#b71c1c,color:#fff
    style MC fill:#e65100,color:#fff
    style MCC fill:#1565c0,color:#fff
    style USER fill:#2e7d32,color:#fff
```

| Vai trò | Mã | Phạm vi | Thuộc về | Nhận cảnh báo |
|---|---|---|---|---|
| **ADMIN** | `ADMIN` | Toàn Tập đoàn | Tập đoàn (không gắn Công ty hay Trung tâm) | T-1 mọi văn bản + toàn bộ GROUP-level |
| **MANAGER_COMPANY** | `MANAGER_COMPANY` | Công ty | Một Công ty (`company_id`) | T-7, T-1 (văn bản Công ty mình + Trung tâm trực thuộc) |
| **MANAGER_CENTER** | `MANAGER_CENTER` | Trung tâm | Một Trung tâm (`department_id`) thuộc Công ty | T-7, T-1 (văn bản Trung tâm mình) |
| **USER** | `USER` | Cá nhân | Một Trung tâm (`department_id`) | T-30, T-15, T-7, T-1 (văn bản của mình) |

### 3.3 Cấp văn bản và quyền duyệt

Mỗi văn bản có field `level` xác định tầm quan trọng và ai có quyền duyệt:

| Cấp văn bản | `level` | Ai có thể duyệt | Ví dụ |
|---|---|---|---|
| **Cấp Trung tâm** | `CENTER` | MANAGER_CENTER (cùng Trung tâm), MANAGER_COMPANY (cùng Công ty), ADMIN | Quy trình nội bộ Trung tâm, SR nội bộ |
| **Cấp Công ty** | `COMPANY` | MANAGER_COMPANY (cùng Công ty), ADMIN | Hợp đồng Công ty, Giấy phép hoạt động |
| **Cấp Tập đoàn** | `GROUP` | ADMIN duy nhất | Văn bản chiến lược, chứng chỉ toàn Tập đoàn |

> **Rule self-approval:** Không role nào được tự duyệt văn bản do chính mình tạo ra — phải nhờ cấp trên một bậc xử lý.

### 3.4 Ma trận phân quyền chi tiết

| Hành động | USER | MANAGER_CENTER | MANAGER_COMPANY | ADMIN |
|---|:---:|:---:|:---:|:---:|
| **Văn bản — Xem** | | | | |
| Xem văn bản của mình | ✅ | ✅ | ✅ | ✅ |
| Xem văn bản Trung tâm mình | ❌ | ✅ | ✅ (Công ty mình) | ✅ |
| Xem văn bản Công ty mình | ❌ | ❌ | ✅ | ✅ |
| Xem tất cả | ❌ | ❌ | ❌ | ✅ |
| **Văn bản — Tạo/Sửa** | | | | |
| Tạo văn bản (bất kỳ cấp) | ✅ | ✅ | ✅ | ✅ |
| Sửa DRAFT của mình | ✅ | ✅ | ✅ | ✅ |
| Sửa DRAFT của người khác | ❌ | ✅ TT mình | ✅ CT mình | ✅ |
| Xóa văn bản DRAFT | ❌ | ✅ TT mình | ✅ CT mình | ✅ |
| Upload file đính kèm | ✅ | ✅ | ✅ | ✅ |
| **Luồng duyệt** | | | | |
| Nộp duyệt (DRAFT → PENDING) | ✅ | ✅ | ✅ | ✅ |
| Duyệt văn bản **CENTER** | ❌ | ✅ TT mình* | ✅ CT mình | ✅ |
| Duyệt văn bản **COMPANY** | ❌ | ❌ | ✅ CT mình* | ✅ |
| Duyệt văn bản **GROUP** | ❌ | ❌ | ❌ | ✅* |
| Từ chối văn bản | ❌ | ✅ (CENTER) | ✅ (CENTER+COMPANY) | ✅ |
| Gia hạn văn bản | ✅ mình | ✅ TT mình | ✅ CT mình | ✅ |
| Thu hồi (→ CANCELLED) | ❌ | ✅ TT mình | ✅ CT mình | ✅ |
| **Cảnh báo** | | | | |
| Xem alert log | Của mình | TT mình | CT mình | Tất cả |
| Cấu hình ngưỡng | ❌ | ❌ | ❌ | ✅ |
| **Hệ thống** | | | | |
| Quản lý người dùng | ❌ | ❌ | ❌ | ✅ |
| Quản lý Trung tâm / Công ty | ❌ | ❌ | ❌ | ✅ |
| Dashboard Trung tâm | ✅ | ✅ | ✅ | ✅ |
| Dashboard Công ty | ❌ | ❌ | ✅ | ✅ |
| Dashboard Tập đoàn | ❌ | ❌ | ❌ | ✅ |

> *\* Trừ văn bản do chính mình tạo ra (self-approval rule).*

### 3.5 Quy tắc lọc văn bản theo vai trò

| Role | Thấy văn bản nào | Điều kiện SQL |
|---|---|---|
| **USER** | Chỉ văn bản mình tạo | `owner_id = :userId` |
| **MANAGER_CENTER** | Tất cả văn bản Trung tâm mình (mọi cấp) | `department_id = :deptId` |
| **MANAGER_COMPANY** | Tất cả văn bản trong Công ty mình (qua join departments.company_id) | `company_id = :companyId` |
| **ADMIN** | Tất cả, không lọc | *(no filter)* |

> **"Văn bản hiện hành":** `status IN ('ACTIVE', 'WARNING')`. Các trạng thái EXPIRED/CANCELLED vẫn lưu để tra cứu lịch sử, không tham gia cron alert.

### 3.6 Phân cấp cảnh báo theo `level` văn bản

```mermaid
graph LR
    subgraph CENTER_DOC["📄 Văn bản cấp CENTER"]
        C30["T-30/15 → USER"]
        C7["T-7 → USER\n+ MANAGER_CENTER"]
        C1["T-1 → USER\n+ MANAGER_CENTER\n+ MANAGER_COMPANY"]
        CEXP["Hết hạn → ADMIN"]
    end

    subgraph COMPANY_DOC["📋 Văn bản cấp COMPANY"]
        P30["T-30/15 → USER"]
        P7["T-7 → USER\n+ MANAGER_COMPANY"]
        P1["T-1 → USER\n+ MANAGER_COMPANY\n+ ADMIN"]
        PEXP["Hết hạn → ADMIN"]
    end

    subgraph GROUP_DOC["🏢 Văn bản cấp GROUP"]
        G30["T-30/15 → USER"]
        G7["T-7 → USER\n+ ADMIN"]
        G1["T-1 → USER\n+ ADMIN"]
        GEXP["Hết hạn → ADMIN"]
    end

    style CENTER_DOC fill:#E3F2FD
    style COMPANY_DOC fill:#FFF3E0
    style GROUP_DOC fill:#FFEBEE
```

| Level | T-30 / T-15 | T-7 | T-1 | Expired |
|---|---|---|---|---|
| **CENTER** | USER (owner) | + MANAGER_CENTER | + MANAGER_COMPANY | ADMIN |
| **COMPANY** | USER (owner) | + MANAGER_COMPANY | + ADMIN | ADMIN |
| **GROUP** | USER (owner) | + ADMIN | + ADMIN | ADMIN |

---

## 4. Luồng nghiệp vụ chính

### 4.1 Luồng tạo và duyệt văn bản

```mermaid
flowchart TD
    A([Bắt đầu]) --> B[USER tạo văn bản\n- Tiêu đề, loại, ngày hết hạn\n- Upload file đính kèm]
    B --> C[Lưu trạng thái: DRAFT]
    C --> D{USER chỉnh sửa\nhoàn tất?}
    D -- Chưa --> C
    D -- Nộp duyệt --> E[Trạng thái: PENDING\nThông báo email → MANAGER]

    E --> F{MANAGER\nxem xét}
    F -- Từ chối + ghi lý do --> G[Trạng thái: REJECTED\nEmail thông báo → USER]
    F -- Duyệt --> H[Trạng thái: ACTIVE\nEmail xác nhận → USER]

    G --> I{USER chỉnh sửa\nlại?}
    I -- Có --> C
    I -- Không --> J([Huỷ bỏ])

    H --> K{{"Cron Job\nchạy mỗi ngày 8:00"}}
    K --> L{Đến ngưỡng\ncảnh báo?}
    L -- Chưa --> K
    L -- T-30 / T-15 --> M[Gửi email → USER]
    L -- T-7 --> N[Gửi email → USER + MANAGER]
    L -- T-1 --> O[Gửi email → USER + MANAGER + ADMIN]
    L -- Hết hạn --> P[Trạng thái: EXPIRED\nEmail → ADMIN]

    M & N & O --> Q{USER / MANAGER\ngia hạn?}
    Q -- Có --> R[Cập nhật expiry_date\nTrạng thái về ACTIVE]
    Q -- Không --> K
    R --> K

    style A fill:#4CAF50,color:#fff
    style K fill:#2196F3,color:#fff
    style M fill:#8BC34A,color:#fff
    style N fill:#FF9800,color:#fff
    style O fill:#f44336,color:#fff
    style P fill:#9C27B0,color:#fff
```

### 4.2 Luồng duyệt văn bản (chi tiết theo vai trò)

```mermaid
sequenceDiagram
    actor U as USER (Nhân viên)
    actor M as MANAGER (Trưởng phòng)
    actor A as ADMIN
    participant SYS as Hệ thống
    participant MAIL as Email

    U->>SYS: Tạo văn bản (DRAFT)
    U->>SYS: Nộp duyệt
    SYS->>SYS: Chuyển DRAFT → PENDING
    SYS->>MAIL: Gửi email thông báo duyệt
    MAIL-->>M: "Có văn bản chờ duyệt"

    alt MANAGER duyệt
        M->>SYS: Phê duyệt
        SYS->>SYS: Chuyển PENDING → ACTIVE
        SYS->>MAIL: Gửi email xác nhận
        MAIL-->>U: "Văn bản đã được duyệt"
    else MANAGER từ chối
        M->>SYS: Từ chối + nhập lý do
        SYS->>SYS: Chuyển PENDING → REJECTED
        SYS->>MAIL: Gửi email từ chối + lý do
        MAIL-->>U: "Văn bản bị từ chối: [lý do]"
        U->>SYS: Chỉnh sửa lại & nộp duyệt lại
    end

    note over SYS: Văn bản ACTIVE → bắt đầu theo dõi hết hạn
```

---

## 5. Kiến trúc hệ thống

### 5.1 Tổng quan Microservices

> Hệ thống tách thành **4 service độc lập**, mỗi service có database schema riêng, giao tiếp với nhau qua REST HTTP. Nginx đóng vai API Gateway định tuyến request từ frontend.

```mermaid
graph TB
    subgraph CLIENT["FRONTEND"]
        FE["Angular SPA\nport 4200"]
    end

    subgraph GATEWAY["API GATEWAY"]
        GW["Nginx\nport 80\n/api/auth/* → auth-service\n/api/documents/* → document-service\n/api/notifications/* → notification-service"]
    end

    subgraph SERVICES["MICROSERVICES"]
        AUTH["auth-service\nport 8081\n─────────────\nLogin / Register\nUser management\nDepartment management\nJWT issue"]
        DOC["document-service\nport 8082\n─────────────\nCRUD documents\nApproval workflow\nFile upload"]
        NOTIF["notification-service\nport 8083\n─────────────\nSend email · Alert config · Alert log\n@Scheduled cron 8h (pull doc-service)\nOutboxRelayConsumer (approval emails)\nAlertQueueProcessor (alert emails)"]
        SCHED["scheduler-service\nport 8084 (internal)\n─────────────\n⚡ Manual trigger proxy only\nPOST /internal/trigger\n→ gọi notification-service"]
    end

    subgraph DATA["DATA LAYER"]
        PG[("PostgreSQL\nauth_schema\ndocument_schema\nnotification_schema")]
        FS["Local Disk\n/uploads"]
        SMTP["Mailhog\nSMTP test"]
    end

    FE -->|HTTP| GW
    GW -->|/api/auth| AUTH
    GW -->|/api/documents| DOC
    GW -->|/api/notifications| NOTIF

    DOC -->|"REST: GET /users/{id}"| AUTH
    DOC -->|"REST: POST /emails/approval"| NOTIF
    SCHED -->|"REST: GET /documents/expiring"| DOC
    SCHED -->|"REST: POST /emails/alert"| NOTIF

    AUTH --> PG
    DOC --> PG
    DOC --> FS
    NOTIF --> PG
    NOTIF --> SMTP

    style AUTH fill:#1565C0,color:#fff
    style DOC fill:#2E7D32,color:#fff
    style NOTIF fill:#E65100,color:#fff
    style SCHED fill:#6A1B9A,color:#fff
    style GW fill:#37474F,color:#fff
```

### 5.2 Phân rã service — trách nhiệm và sở hữu dữ liệu

```mermaid
graph LR
    subgraph AS["auth-service 🔵"]
        A1[POST /auth/login]
        A2[POST /auth/register]
        A3[GET /users]
        A4[POST /users]
        A5[PUT /users/:id]
        A6[GET /departments]
        DB_A[("auth_schema\nusers\ndepartments")]
    end

    subgraph DS["document-service 🟢"]
        D1[GET /documents]
        D2[POST /documents]
        D3[POST /documents/:id/submit]
        D4[POST /documents/:id/approve]
        D5[POST /documents/:id/reject]
        D6[POST /documents/:id/renew]
        D7[POST /documents/:id/upload]
        DB_D[("document_schema\ndocuments\napproval_requests")]
    end

    subgraph NS["notification-service 🟠"]
        N1[GET /alert-configs]
        N2[PUT /alert-configs/:id]
        N3[GET /alert-logs]
        N4[GET /dashboard/stats]
        DB_N[("notification_schema\nalert_configs\nalert_logs")]
    end

    subgraph SS["scheduler-service 🟣 (thin proxy)"]
        S1["POST /internal/trigger\n→ gọi NOTIF /internal/trigger"]
    end
```

### 5.3 Giao tiếp giữa các service

```mermaid
sequenceDiagram
    participant FE as Angular
    participant GW as Nginx Gateway
    participant DOC as document-service
    participant AUTH as auth-service
    participant NOTIF as notification-service
    participant SCHED as scheduler-service

    note over FE,GW: Mọi request đều qua Gateway
    FE->>GW: POST /api/documents/:id/approve\nAuthorization: Bearer <JWT>
    GW->>DOC: Forward request + JWT header

    note over DOC,AUTH: document-service tự verify JWT\nbằng shared JWT_SECRET (không gọi auth-service)
    DOC->>DOC: Verify JWT locally

    DOC->>DOC: Cập nhật status = ACTIVE
    DOC->>NOTIF: POST http://notification-service:8083/internal/emails\n{type: "APPROVED", docId, ownerEmail}
    NOTIF-->>DOC: 200 OK
    DOC-->>GW: 200 {document}
    GW-->>FE: 200 {document}

    note over SCHED,NOTIF: Scheduler chạy nội bộ, không qua Gateway
    SCHED->>DOC: GET http://document-service:8082/internal/expiring
    DOC-->>SCHED: [{docId, ownerEmail, managerEmail, daysLeft}]
    SCHED->>NOTIF: POST http://notification-service:8083/internal/alerts\n{docId, daysLeft, recipients[]}
    NOTIF-->>SCHED: 200 OK
```

### 5.4 Ghi chú kiến trúc — Shared Database vs. True Microservices

> **Phản hồi reviewer:** *"Nếu micro thì cần thận làm có chia độc lập ko, có thì nên thế nào, ko thì thế nào."*

Thiết kế hiện tại là **"Shared Database, Schema per Service"** — một mẫu thực dụng cho miniproject, **không phải** true microservices hoàn chỉnh. Cần hiểu rõ sự khác biệt:

| Tiêu chí | Thiết kế hiện tại (v1.0) | True Microservices (hướng tới) |
|---|---|---|
| **Database** | 1 PostgreSQL instance, 3 schema riêng | Mỗi service có PostgreSQL **riêng** |
| **Isolation** | Schema-level (không dùng FK chéo schema) | Process-level + Network-level |
| **Giao tiếp** | REST đồng bộ (document-service → auth-service) | Event-driven (Kafka/RabbitMQ) |
| **Deploy độc lập** | Có thể, nhưng share DB là điểm nghẽn | Hoàn toàn độc lập |
| **Phù hợp với** | Demo 3 tuần, team 1 người | Production, team lớn |

**Lý do chọn shared-DB cho miniproject:**
- Đơn giản hóa việc setup Docker Compose
- Không cần implement event sourcing (phức tạp)
- Flyway migration dễ quản lý hơn khi cùng instance
- Mục tiêu là demo nghiệp vụ, không phải production-grade

**Điểm cần chú ý trong báo cáo:** Phải giải thích rõ đây là quyết định có chủ đích, không phải thiếu hiểu biết về microservices. Trình bày lộ trình v2.0 (tách DB + async) để chứng minh hiểu bài toán.

**Ràng buộc quan trọng:** Dù share cùng PostgreSQL instance, **các service KHÔNG được query trực tiếp schema của service khác**. document-service muốn lấy thông tin user phải gọi `GET /internal/users/{id}` của auth-service — không được `JOIN` chéo schema trong SQL.

### 5.5 Sơ đồ triển khai — Docker Compose

```mermaid
graph TB
    subgraph HOST["Máy chủ / Laptop sinh viên — Docker Compose"]
        subgraph FRONT["Public ports"]
            FEC["frontend\nAngular :4200"]
            GWC["nginx\nAPI Gateway :80"]
        end

        subgraph SVC["Services (internal network)"]
            AUTHC["auth-service\n:8081"]
            DOCC["document-service\n:8082"]
            NOTIFC["notification-service\n:8083"]
            SCHEDC["scheduler-service\n:8084"]
        end

        subgraph INFRA["Infrastructure (internal)"]
            PGC[("postgres\n:5432")]
            MHC["mailhog\nSMTP :1025\nWeb UI :8025"]
        end
    end

    BROWSER["Trình duyệt"] --> FEC
    BROWSER --> GWC
    DEV["Dev\nxem mail"] --> MHC

    GWC --> AUTHC & DOCC & NOTIFC
    DOCC -->|REST| AUTHC
    DOCC -->|REST| NOTIFC
    SCHEDC -->|REST| DOCC & NOTIFC

    AUTHC & DOCC & NOTIFC --> PGC
    NOTIFC --> MHC

    style AUTHC fill:#1565C0,color:#fff
    style DOCC fill:#2E7D32,color:#fff
    style NOTIFC fill:#E65100,color:#fff
    style SCHEDC fill:#6A1B9A,color:#fff
    style GWC fill:#37474F,color:#fff
    style FEC fill:#DD0031,color:#fff
    style PGC fill:#336791,color:#fff
    style MHC fill:#FF6B35,color:#fff
```

---

## 6. Bảng công nghệ

| Tầng | Thành phần | Công nghệ | Ghi chú |
|---|---|---|---|
| **Frontend** | Web SPA | Angular 17+ + Angular Material | Built-in module system, phù hợp enterprise |
| **Frontend** | HTTP Client | Angular HttpClient + Interceptor | Tự động gắn JWT, handle 401/403 |
| **Frontend** | Router | Angular Router + `CanActivate` | Guard route theo role |
| **API Gateway** | Reverse proxy | Nginx | Định tuyến request theo path prefix đến đúng service |
| **auth-service** | REST API + Auth | Spring Boot 3 + Spring Security + JWT | Phát hành JWT, validate role; shared `JWT_SECRET` với các service khác |
| **document-service** | REST API | Spring Boot 3 | Verify JWT cục bộ bằng shared secret, gọi notification-service qua REST |
| **notification-service** | REST API + Email | Spring Boot 3 + Spring Mail | Nhận request từ document-service và scheduler-service, gửi email qua SMTP |
| **scheduler-service** | Cron | Spring Boot 3 + `@Scheduled` | Gọi document-service lấy danh sách, gọi notification-service gửi alert |
| **Database** | Chính | PostgreSQL 15 — 3 schema riêng | `auth_schema`, `document_schema`, `notification_schema` trong cùng 1 instance |
| **Database** | Migration | Flyway (mỗi service tự migrate schema của mình) | Init schema khi service khởi động |
| **File Storage** | Upload | Local disk `/uploads` (mount volume Docker) | Không cần S3 cho demo |
| **Email test** | Dev mail | Mailhog | Bắt SMTP local, xem qua web UI port 8025 |
| **Container** | Orchestration | Docker + Docker Compose | 7 container, 1 internal network `app-network` |

### Cấu hình Nginx routing (minh hoạ)

```nginx
# nginx.conf
location /api/auth/ {
    proxy_pass http://auth-service:8081/;
}
location /api/documents/ {
    proxy_pass http://document-service:8082/;
}
location /api/notifications/ {
    proxy_pass http://notification-service:8083/;
}
```

---

## 7. Mô hình dữ liệu

> Mỗi service sở hữu schema riêng trong cùng một PostgreSQL instance. Service không truy cập trực tiếp vào schema của service khác — phải gọi qua REST API.

```mermaid
erDiagram
    %% ─── auth_schema (sở hữu bởi auth-service) ───
    COMPANIES {
        int id PK
        varchar name
        varchar code
    }

    DEPARTMENTS {
        int id PK
        varchar name
        varchar code
        int company_id FK
    }

    USERS {
        int id PK
        varchar email
        varchar password_hash
        varchar full_name
        varchar role
        int department_id FK
        int company_id FK
        boolean is_active
        timestamp created_at
    }

    %% ─── document_schema (sở hữu bởi document-service) ───
    DOCUMENTS {
        int id PK
        varchar code
        varchar title
        varchar type
        varchar level
        date issue_date
        date expiry_date
        varchar status
        varchar file_path
        varchar note
        int owner_id
        int department_id
        int company_id
        int renewal_count
        timestamp created_at
        timestamp updated_at
    }

    APPROVAL_REQUESTS {
        int id PK
        int document_id FK
        int requested_by
        int reviewed_by
        varchar action
        text comment
        timestamp requested_at
        timestamp reviewed_at
    }

    %% ─── document_schema — Outbox (sở hữu bởi document-service) ───
    NOTIFICATION_OUTBOX {
        int id PK
        varchar event_type
        int document_id
        jsonb payload
        varchar status
        int retry_count
        timestamp created_at
        timestamp sent_at
    }

    %% ─── notification_schema (sở hữu bởi notification-service) ───
    ALERT_CONFIGS {
        int id PK
        varchar document_type
        varchar document_level
        varchar remind_days
        boolean is_active
    }

    ALERT_QUEUE {
        int id PK
        int document_id
        varchar doc_level
        int days_left
        jsonb payload
        varchar status
        int retry_count
        timestamp queued_at
        timestamp processed_at
    }

    ALERT_LOGS {
        int id PK
        int document_id
        int days_before
        varchar recipient_role
        varchar recipient_email
        timestamp sent_at
        varchar status
    }

    COMPANIES ||--o{ DEPARTMENTS : "auth_schema"
    DEPARTMENTS ||--o{ USERS : "auth_schema"
    COMPANIES ||--o{ USERS : "MANAGER_COMPANY (company_id trực tiếp)"
    USERS ||--o{ DOCUMENTS : "owner_id (không FK chéo schema)"
    DOCUMENTS ||--o{ APPROVAL_REQUESTS : "document_schema"
    DOCUMENTS ||--o{ NOTIFICATION_OUTBOX : "document_schema (outbox)"
    DOCUMENTS ||--o{ ALERT_QUEUE : "notification_schema (không FK chéo schema)"
    DOCUMENTS ||--o{ ALERT_LOGS : "document_id (không FK chéo schema)"
```

### Ghi chú thiết kế

| Bảng | Service sở hữu | Điểm chú ý |
|---|---|---|
| `companies` | auth-service | Đơn vị cấp Công ty. Seed ít nhất 1 công ty mặc định |
| `departments` | auth-service | Trung tâm — có `company_id` FK sang `companies`. `role` CHECK: `ADMIN`/`MANAGER_COMPANY`/`MANAGER_CENTER`/`USER` |
| `users` | auth-service | `department_id`: bắt buộc với USER và MANAGER_CENTER. `company_id`: bắt buộc với MANAGER_COMPANY. ADMIN: cả hai NULL |
| `documents` | document-service | Thêm `level` CHECK(`CENTER`,`COMPANY`,`GROUP`). Thêm `company_id` (lấy từ JWT claim khi tạo). `renewal_count` tăng mỗi lần gia hạn |
| `approval_requests` | document-service | `action` = `SUBMIT`/`APPROVE`/`REJECT`/`RENEW`/`CANCEL`; toàn bộ lịch sử |
| `notification_outbox` | document-service | **Outbox table.** `event_type` = `APPROVAL_REQUEST`/`APPROVED`/`REJECTED`. `payload` JSON chứa đủ thông tin để notification-service gửi mail (không cần query thêm). `status` = `PENDING`→`SENT`/`FAILED`. Max 3 lần retry |
| `alert_queue` | notification-service | **Inbox/Queue table.** Scheduler ghi vào đây qua REST (202 Accepted). `AlertQueueProcessor` xử lý async mỗi 30s. Dedup với `alert_logs` vẫn giữ nguyên |
| `alert_configs` | notification-service | Thêm `document_level` để cấu hình ngưỡng riêng theo cấp. Default `"30,15,7,1"` cho tất cả |
| `alert_logs` | notification-service | `document_id` lưu ID tham chiếu, **không FK** chéo schema; check trùng trước khi gửi |

**Nullable rules cho `users`:**

| Role | `department_id` | `company_id` |
|---|---|---|
| USER | ✅ Bắt buộc (Trung tâm thuộc về) | ❌ NULL (lấy qua departments.company_id) |
| MANAGER_CENTER | ✅ Bắt buộc (Trung tâm quản lý) | ❌ NULL (lấy qua departments.company_id) |
| MANAGER_COMPANY | ❌ NULL | ✅ Bắt buộc (Công ty quản lý) |
| ADMIN | ❌ NULL | ❌ NULL |

**Về gia hạn:** cập nhật tại chỗ (`expiry_date` mới + `renewal_count++`) + ghi log `RENEW` vào `approval_requests`.

> **Lý do không dùng Foreign Key chéo schema:** Mỗi service là đơn vị độc lập — nếu dùng FK chéo schema thì việc migrate, tách, hoặc scale riêng service sẽ không thực hiện được.

---

## 8. Luồng thông báo — Outbox Pattern

Hệ thống có **2 flow thông báo** áp dụng outbox pattern theo cách khác nhau do đặc thù của từng flow:

| | Flow 1 — Approval | Flow 2 — Alert (Cron) |
|---|---|---|
| **Trigger** | Đổi status văn bản | Cron job hàng ngày |
| **Outbox nằm ở** | `document_schema.notification_outbox` | `notification_schema.alert_queue` |
| **Relay/Processor** | `OutboxRelayJob` trong document-service | `AlertQueueProcessor` trong notification-service |
| **Lý do tách** | document-service sở hữu event, phải atomic với transaction | notification-service sở hữu queue, scheduler chỉ enqueue |

---

### 8.1 Flow 1 — Approval Notification (Outbox trong document-service)

```mermaid
sequenceDiagram
    actor USER
    participant DOC as document-service
    participant PG_D as document_schema
    participant RELAY as OutboxRelayJob\n@Scheduled(10s)
    participant NOTIF as notification-service
    participant SMTP as Mailhog / SMTP

    USER->>DOC: POST /documents/{id}/approve
    DOC->>PG_D: BEGIN TRANSACTION
    DOC->>PG_D: UPDATE documents SET status='ACTIVE'
    DOC->>PG_D: INSERT notification_outbox\n(type='APPROVED', payload={docId, title,\nownerEmail, ...}, status='PENDING')
    DOC->>PG_D: COMMIT  ← atomic: cả 2 hoặc không có gì
    DOC-->>USER: 200 OK {document}

    note over RELAY: Chạy mỗi 10 giây
    RELAY->>PG_D: SELECT * FROM notification_outbox\nWHERE status='PENDING' LIMIT 50
    PG_D-->>RELAY: [outbox entries]

    loop Mỗi entry
        RELAY->>NOTIF: POST /internal/emails\n{type, payload}
        alt Thành công
            NOTIF->>SMTP: Gửi email
            NOTIF-->>RELAY: 200 OK
            RELAY->>PG_D: UPDATE outbox SET status='SENT'
        else Thất bại (NOTIF down)
            RELAY->>PG_D: UPDATE retry_count++\n(status='FAILED' nếu retry >= 3)
        end
    end
```

**Điểm cốt lõi:** Nếu notification-service down, `UPDATE documents` vẫn commit thành công. Outbox entry chờ relay retry. Không mất event, không rollback nghiệp vụ.

---

### 8.2 Flow 2 — Alert Notification (notification-service tự pull + true outbox)

> **Thay đổi v1.6:** notification-service tự sở hữu cron logic. Không còn phụ thuộc scheduler push vào — notification-service pull document-service và ghi thẳng vào `alert_queue` trong cùng schema → true outbox. scheduler-service chỉ là proxy cho manual trigger.

```mermaid
sequenceDiagram
    participant SCHED as scheduler-service\n(thin proxy)
    participant NOTIF as notification-service\n@Scheduled(cron 8h)
    participant DOC as document-service
    participant AUTH as auth-service
    participant PG_N as notification_schema
    participant PROC as AlertQueueProcessor\n@Scheduled(30s)
    participant SMTP as Mailhog / SMTP

    note over NOTIF: @Scheduled(cron="0 0 8 * * *")\nHOẶC trigger thủ công qua SCHED

    opt Manual trigger (dev/test)
        SCHED->>NOTIF: POST /internal/trigger
    end

    NOTIF->>DOC: GET /internal/documents/expiring
    DOC->>AUTH: Lấy emails owner / manager / admin theo level
    DOC-->>NOTIF: [{docId, level, daysLeft, ownerEmail,\ncenterMgrEmail, companyMgrEmail, adminEmail}]

    loop Mỗi document — trong cùng service, cùng DB
        NOTIF->>NOTIF: resolveRecipients(level × daysLeft)
        NOTIF->>PG_N: INSERT alert_queue\n(status='PENDING')  ← không qua REST, không mất event
    end

    note over PROC: Chạy mỗi 30 giây
    PROC->>PG_N: SELECT * FROM alert_queue WHERE status='PENDING'
    loop Mỗi item
        PROC->>PG_N: Kiểm tra alert_logs (chống trùng)
        PROC->>SMTP: Gửi email theo recipients[]
        alt daysLeft <= 0
            PROC->>DOC: PATCH /internal/documents/{id}/status {status:"EXPIRED"}
        else daysLeft = 7
            PROC->>DOC: PATCH /internal/documents/{id}/status {status:"WARNING"}
        end
        PROC->>PG_N: INSERT alert_logs + UPDATE alert_queue status='DONE'
    end
```

**Điểm cốt lõi:** notification-service ghi vào `alert_queue` mà không qua REST → không có cửa sổ mất event. Nếu document-service down lúc 8h, chỉ fail bước pull (có thể retry); nếu SMTP down, `AlertQueueProcessor` retry sau 30s.

---

### 8.3 Logic resolve recipients (giữ nguyên)

```java
private List<RecipientInfo> resolveRecipients(ExpiringDocDto doc, long daysLeft) {
    List<RecipientInfo> recipients = new ArrayList<>();
    recipients.add(doc.getOwner());

    switch (doc.getLevel()) {
        case "CENTER" -> {
            if (daysLeft <= 7)  recipients.add(doc.getCenterManager());
            if (daysLeft <= 1)  recipients.add(doc.getCompanyManager());
        }
        case "COMPANY" -> {
            if (daysLeft <= 7)  recipients.add(doc.getCompanyManager());
            if (daysLeft <= 1)  recipients.add(doc.getAdmin());
        }
        case "GROUP" -> {
            if (daysLeft <= 7)  recipients.add(doc.getAdmin());
        }
    }
    if (daysLeft <= 0) {
        recipients.clear();
        recipients.add(doc.getAdmin());
    }
    return recipients;
}
```

---

## 9. Vòng đời trạng thái văn bản

```mermaid
stateDiagram-v2
    [*] --> DRAFT : USER tạo mới

    DRAFT --> PENDING : USER nộp duyệt\n→ Email thông báo Manager cấp tương ứng
    DRAFT --> CANCELLED : USER / ADMIN huỷ

    PENDING --> ACTIVE : Manager cấp tương ứng phê duyệt\n(CENTER→MGR_CENTER, COMPANY→MGR_COMPANY, GROUP→ADMIN)\n→ Email xác nhận USER
    PENDING --> REJECTED : Manager từ chối + lý do\n→ Email thông báo USER
    PENDING --> CANCELLED : ADMIN huỷ

    REJECTED --> DRAFT : USER chỉnh sửa lại
    REJECTED --> CANCELLED : USER / ADMIN huỷ

    ACTIVE --> WARNING : Cron phát hiện T-7\n→ Email theo cấp văn bản
    WARNING --> ACTIVE : USER / Manager cấp tương ứng gia hạn

    WARNING --> EXPIRED : Cron: expiry_date < today
    ACTIVE --> EXPIRED : Hết hạn không qua cảnh báo
    EXPIRED --> ACTIVE : Manager cấp tương ứng / ADMIN gia hạn hồi tố

    ACTIVE --> CANCELLED : Manager cấp tương ứng / ADMIN thu hồi
    WARNING --> CANCELLED : Manager cấp tương ứng / ADMIN thu hồi

    CANCELLED --> [*]
    EXPIRED --> [*] : Lưu trữ sau 1 năm

    note right of PENDING : Chờ duyệt — không\ntheo dõi hết hạn
    note right of WARNING : Email phân cấp\nT-30/15 → User\nT-7 → User + Manager\nT-1 → Tất cả
    note right of EXPIRED : Email báo cáo → Admin\nHiển thị nổi bật Dashboard
```

---

## 10. Khả năng mở rộng & tích hợp

### 10.1 Lộ trình mở rộng kiến trúc

```mermaid
graph LR
    subgraph V1["v1.0 — Miniproject\n3 tuần"]
        direction TB
        A1[4 Microservices\nSpring Boot]
        A2[(PostgreSQL\n3 schema)]
        A3[Docker Compose]
        A4[REST sync\ninter-service]
        A5[Luồng duyệt 2 cấp]
    end

    subgraph V2["v2.0 — Nâng cấp"]
        direction TB
        B1[Thêm RabbitMQ\nasync events]
        B2[Thêm SMS / Zalo OA]
        B3[Luồng duyệt N cấp]
        B4[Deploy VPS / Cloud]
    end

    subgraph V3["v3.0 — Production"]
        direction TB
        C1[Kubernetes\nHPA auto-scale]
        C2[Kafka event bus]
        C3[CI/CD Pipeline]
        C4[Tích hợp ERP / AD]
    end

    V1 -->|Thay REST bằng async| V2 -->|Scale up| V3
```

### 10.2 Các tích hợp có thể bổ sung sau

| Hệ thống | Phương thức | Mục đích | Độ ưu tiên |
|---|---|---|---|
| **Zalo OA** | Zalo API | Thay / bổ sung kênh email | Cao |
| **Google / Microsoft SSO** | OAuth2 | Đăng nhập tiện hơn | Trung bình |
| **Webhook generic** | HTTP POST | Tích hợp hệ thống nội bộ | Trung bình |
| **SMS (ESMS/FPT)** | REST API | Cảnh báo khẩn T-1 | Thấp |
| **Active Directory** | LDAP Sync | Đồng bộ user/phòng ban | Thấp |

---

## 11. Kế hoạch 3 tuần

```mermaid
gantt
    title Kế hoạch Miniproject — Quản lý Hết hạn Văn bản (Microservices)
    dateFormat  YYYY-MM-DD
    axisFormat  %d/%m

    section Tuần 1 — Hạ tầng & Auth
    Thiết kế schema, phân rã service       :done, t1a, 2026-06-15, 1d
    Docker Compose + Nginx config          :t1b, 2026-06-15, 2d
    auth-service: login/register/JWT       :t1c, 2026-06-16, 2d
    auth-service: user & department API    :t1d, 2026-06-18, 1d
    Frontend Angular: setup + login        :t1e, 2026-06-16, 3d
    Frontend: HTTP interceptor + JWT guard :t1f, 2026-06-19, 1d

    section Tuần 2 — Document & Notification
    document-service: CRUD + upload        :t2a, 2026-06-22, 2d
    document-service: approval workflow    :t2b, 2026-06-23, 2d
    notification-service: email + config   :t2c, 2026-06-22, 2d
    scheduler-service: cron + REST calls   :t2d, 2026-06-25, 2d
    Frontend: document list, form, duyệt   :t2e, 2026-06-22, 4d
    Frontend: alert log UI                 :t2f, 2026-06-26, 1d

    section Tuần 3 — Hoàn thiện
    Dashboard thống kê theo role           :t3a, 2026-06-29, 2d
    Gia hạn + thu hồi văn bản              :t3b, 2026-06-29, 1d
    Kiểm thử inter-service + fix bug       :t3c, 2026-07-01, 2d
    Viết báo cáo + slide demo              :t3d, 2026-07-03, 2d
    Demo & nộp bài                         :milestone, 2026-07-05, 0d
```

### Phân chia công việc gợi ý (nhóm 3–4 người)

| Vai trò | Tuần 1 | Tuần 2 | Tuần 3 |
|---|---|---|---|
| **Backend dev 1** | auth-service (login, JWT, user, dept) | document-service (CRUD, upload, approval) | Gia hạn, inter-service test, fix bug |
| **Backend dev 2** | Docker Compose + Nginx routing | notification-service (email, alert config, log) + scheduler-service | Dashboard API, deploy |
| **Frontend dev** | Angular setup, login, JWT interceptor, route guard | Document list, form tạo/sửa, luồng duyệt Manager | Dashboard, alert log, UI polish |
| **Lead / Fullstack** | Thiết kế schema, phân rã service, review | Kết nối các service, test Postman end-to-end | Báo cáo, slide, chuẩn bị demo |

---

## 12. Danh sách API

> Prefix `/api/auth`, `/api/documents`, `/api/notifications` được Nginx routing đến đúng service. Các endpoint `/internal/*` chỉ dùng nội bộ giữa các service, **không expose** ra ngoài qua Gateway.

### auth-service — `/api/auth/*`

| Method | Endpoint | Mô tả | Quyền |
|---|---|---|---|
| `POST` | `/api/auth/login` | Đăng nhập, nhận JWT | Public |
| `POST` | `/api/auth/register` | Tạo tài khoản (Admin tạo cho user) | ADMIN |
| `GET` | `/api/auth/users` | Danh sách người dùng | ADMIN |
| `PUT` | `/api/auth/users/{id}` | Cập nhật thông tin, đổi role | ADMIN |
| `GET` | `/api/auth/departments` | Danh sách phòng ban | ADMIN, MANAGER |
| `POST` | `/api/auth/departments` | Tạo phòng ban | ADMIN |

### document-service — `/api/documents/*`

| Method | Endpoint | Mô tả | Quyền |
|---|---|---|---|
| `GET` | `/api/documents` | Danh sách văn bản (lọc theo role) | ALL |
| `POST` | `/api/documents` | Tạo văn bản mới (DRAFT) | ALL |
| `GET` | `/api/documents/{id}` | Chi tiết văn bản | ALL |
| `PUT` | `/api/documents/{id}` | Cập nhật văn bản | Owner, MANAGER, ADMIN |
| `DELETE` | `/api/documents/{id}` | Xóa văn bản | ADMIN, MANAGER (phòng) |
| `POST` | `/api/documents/{id}/upload` | Upload file đính kèm | Owner, MANAGER, ADMIN |
| `GET` | `/api/documents/expiring` | Danh sách sắp hết hạn | ALL |
| `POST` | `/api/documents/{id}/submit` | Nộp duyệt (DRAFT → PENDING) | Owner |
| `POST` | `/api/documents/{id}/approve` | Phê duyệt (PENDING → ACTIVE) | MANAGER, ADMIN |
| `POST` | `/api/documents/{id}/reject` | Từ chối + ghi lý do | MANAGER, ADMIN |
| `POST` | `/api/documents/{id}/renew` | Gia hạn văn bản | Owner, MANAGER, ADMIN |
| `POST` | `/api/documents/{id}/cancel` | Thu hồi / Hủy | MANAGER, ADMIN |
| `GET` | `/api/documents/{id}/approvals` | Lịch sử duyệt | ALL |
| `GET` | `/internal/documents/expiring` | **[Internal]** Lấy danh sách sắp hết hạn | scheduler-service only |
| `PATCH` | `/internal/documents/{id}/status` | **[Internal]** Cập nhật status | notification-service only |

### notification-service — `/api/notifications/*`

| Method | Endpoint | Mô tả | Quyền |
|---|---|---|---|
| `GET` | `/api/notifications/alert-configs` | Danh sách cấu hình ngưỡng | ADMIN |
| `PUT` | `/api/notifications/alert-configs/{id}` | Cập nhật ngưỡng cảnh báo | ADMIN |
| `GET` | `/api/notifications/alert-logs` | Lịch sử cảnh báo (lọc theo role) | ALL |
| `GET` | `/api/notifications/dashboard/stats` | Thống kê tổng hợp theo role | ALL |
| `POST` | `/internal/alerts` | **[Internal]** Trigger gửi cảnh báo | scheduler-service only |
| `POST` | `/internal/emails/approval` | **[Internal]** Gửi email duyệt/từ chối | document-service only |

---

*Tài liệu này là bản draft phục vụ miniproject sinh viên. Scope, công nghệ và timeline có thể điều chỉnh theo yêu cầu của giảng viên hướng dẫn.*
