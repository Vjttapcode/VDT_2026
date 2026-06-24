# Kế hoạch 3 Tuần — 1 Người

> **Dự án:** Hệ thống Quản lý & Cảnh báo Hết hạn Văn bản / SR
> **Thời gian:** 17/06 → 05/07/2026
> **Người thực hiện:** 1 người (solo developer)
> **Công nghệ:** Spring Boot 3 · Angular 17 · PostgreSQL · Docker Compose

---

## Mục lục

1. [Tổng quan chiến lược](#1-tổng-quan-chiến-lược)
2. [Tuần 1 — Hạ tầng + auth-service + Angular skeleton](#2-tuần-1-1721--2106)
3. [Tuần 2 — document-service + notification + scheduler + Frontend core](#3-tuần-2-2206--2806)
4. [Tuần 3 — Dashboard + Gia hạn + Polish + Demo](#4-tuần-3-2906--0507)
5. [Bảng tóm tắt theo ngày](#5-bảng-tóm-tắt-theo-ngày)
6. [Lưu ý quan trọng cho solo developer](#6-lưu-ý-quan-trọng-cho-solo-developer)

---

## 1. Tổng quan chiến lược

Vì chỉ có 1 người, cần build theo thứ tự **vertical slice** (dọc từ DB lên UI) thay vì ngang từng service, để lúc nào cũng có thứ chạy được và test được.

**Thứ tự ưu tiên:**

| Bước | Thành phần | Lý do |
|------|-----------|-------|
| 1 | Hạ tầng (Docker, Nginx, DB) | Nền tảng cho mọi thứ |
| 2 | auth-service | Cần JWT cho mọi service khác |
| 3 | document-service | Nghiệp vụ cốt lõi |
| 4 | notification + scheduler | Feature bổ sung |
| 5 | Frontend | Build song song với backend từ tuần 2 |

---

## 2. Tuần 1 (17/06 – 21/06)

**Hạ tầng + auth-service + Angular skeleton**

> **Mục tiêu cuối tuần:** Đăng nhập được, JWT được cấp, route guard theo role hoạt động, Docker Compose chạy toàn bộ hạ tầng.

---

### Ngày 1 (17/06 Thứ 3) — Hạ tầng & project scaffold

#### Task 1.1 — Tạo cấu trúc thư mục

```
vdt-miniproject/
├── docker-compose.yml
├── nginx/nginx.conf
├── auth-service/          # Spring Initializr
├── document-service/      # Spring Initializr
├── notification-service/  # Spring Initializr
├── scheduler-service/     # Spring Initializr
└── frontend/              # ng new
```

**Spring Initializr dependencies cho mỗi service:**

| Service | Dependencies cần chọn |
|---------|----------------------|
| auth-service | Spring Web, Spring Security, Spring Data JPA, PostgreSQL Driver, Flyway, Lombok |
| document-service | Spring Web, Spring Security, Spring Data JPA, PostgreSQL Driver, Flyway, Lombok |
| notification-service | Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway, Lombok, Java Mail Sender |
| scheduler-service | Spring Web, Lombok |

Thêm dependency JWT vào `pom.xml` của auth-service và document-service:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

---

#### Task 1.2 — Tạo `docker-compose.yml`

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: vdt_db
      POSTGRES_USER: vdt
      POSTGRES_PASSWORD: vdt123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - app-network

  mailhog:
    image: mailhog/mailhog
    ports:
      - "1025:1025"   # SMTP
      - "8025:8025"   # Web UI — mở http://localhost:8025 để xem email
    networks:
      - app-network

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - auth-service
      - document-service
      - notification-service
    networks:
      - app-network

  auth-service:
    build: ./auth-service
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/vdt_db
      SPRING_DATASOURCE_USERNAME: vdt
      SPRING_DATASOURCE_PASSWORD: vdt123
      JWT_SECRET: your-256-bit-secret-key-here-minimum-32-chars
      JWT_EXPIRATION: 86400000
    depends_on:
      - postgres
    networks:
      - app-network

  document-service:
    build: ./document-service
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/vdt_db
      SPRING_DATASOURCE_USERNAME: vdt
      SPRING_DATASOURCE_PASSWORD: vdt123
      JWT_SECRET: your-256-bit-secret-key-here-minimum-32-chars
      NOTIFICATION_SERVICE_URL: http://notification-service:8083
      AUTH_SERVICE_URL: http://auth-service:8081
    volumes:
      - uploads:/app/uploads
    depends_on:
      - postgres
      - auth-service
    networks:
      - app-network

  notification-service:
    build: ./notification-service
    ports:
      - "8083:8083"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/vdt_db
      SPRING_DATASOURCE_USERNAME: vdt
      SPRING_DATASOURCE_PASSWORD: vdt123
      SPRING_MAIL_HOST: mailhog
      SPRING_MAIL_PORT: 1025
      DOCUMENT_SERVICE_URL: http://document-service:8082
    depends_on:
      - postgres
      - mailhog
    networks:
      - app-network

  scheduler-service:
    build: ./scheduler-service
    ports:
      - "8084:8084"
    environment:
      DOCUMENT_SERVICE_URL: http://document-service:8082
      NOTIFICATION_SERVICE_URL: http://notification-service:8083
    depends_on:
      - document-service
      - notification-service
    networks:
      - app-network

volumes:
  postgres_data:
  uploads:

networks:
  app-network:
    driver: bridge
```

> **Tip:** Chạy `docker-compose up postgres mailhog` trước để test DB và mail, sau đó mới build các service.

---

#### Task 1.3 — `nginx/nginx.conf`

```nginx
events {
    worker_connections 1024;
}

http {
    upstream auth    { server auth-service:8081; }
    upstream docs    { server document-service:8082; }
    upstream notif   { server notification-service:8083; }

    server {
        listen 80;

        # Forward Authorization header để các service nhận được JWT
        proxy_set_header Authorization $http_authorization;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        location /api/auth/ {
            proxy_pass http://auth/;
        }

        location /api/documents/ {
            proxy_pass http://docs/;
        }

        location /api/notifications/ {
            proxy_pass http://notif/;
        }

        # Phục vụ file đính kèm đã upload
        location /uploads/ {
            alias /app/uploads/;
        }
    }
}
```

---

### Ngày 2–3 (18–19/06) — auth-service

#### Task 1.4 — Flyway migration `V1__init_auth_schema.sql`

Đặt file tại `auth-service/src/main/resources/db/migration/V1__init_auth_schema.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS auth_schema;
SET search_path TO auth_schema;

-- Cấp Công ty (Company)
CREATE TABLE companies (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20)  UNIQUE NOT NULL
);

-- Cấp Trung tâm (Center) — thuộc về một Công ty
CREATE TABLE departments (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(20)  UNIQUE NOT NULL,
    company_id INTEGER REFERENCES companies(id)
);

-- Users — 4 roles tương ứng 3 cấp tổ chức
-- USER & MANAGER_CENTER: gắn department_id (Trung tâm)
-- MANAGER_COMPANY: gắn company_id, department_id NULL
-- ADMIN: cả hai NULL (phạm vi toàn Tập đoàn)
CREATE TABLE users (
    id            SERIAL PRIMARY KEY,
    email         VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL
                  CHECK (role IN ('ADMIN', 'MANAGER_COMPANY', 'MANAGER_CENTER', 'USER')),
    department_id INTEGER REFERENCES departments(id),   -- NULL nếu là MANAGER_COMPANY hoặc ADMIN
    company_id    INTEGER REFERENCES companies(id),     -- NULL nếu là USER / MANAGER_CENTER / ADMIN (dùng departments.company_id)
    is_active     BOOLEAN   DEFAULT true,
    created_at    TIMESTAMP DEFAULT NOW()
);

-- Seed Công ty mặc định
INSERT INTO companies (name, code) VALUES ('Công ty A', 'CTA');

-- Seed Trung tâm
INSERT INTO departments (name, code, company_id) VALUES
    ('Trung tâm IT',      'TT-IT', 1),
    ('Trung tâm Kế toán', 'TT-KT', 1),
    ('Trung tâm Pháp chế','TT-PC', 1);

-- Seed Admin Tập đoàn (password: Admin@123)
INSERT INTO users (email, password_hash, full_name, role)
VALUES ('admin@tapDoan.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'Admin Tập Đoàn', 'ADMIN');

-- Seed Manager Công ty
INSERT INTO users (email, password_hash, full_name, role, company_id)
VALUES ('manager.cty@tapDoan.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'Trưởng Công ty A', 'MANAGER_COMPANY', 1);

-- Seed Manager Trung tâm IT
INSERT INTO users (email, password_hash, full_name, role, department_id)
VALUES ('manager.tt@tapDoan.vn',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'Trưởng TT IT', 'MANAGER_CENTER', 1);
```

Cấu hình Flyway trong `application.yml`:
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  flyway:
    schemas: auth_schema
    default-schema: auth_schema
  jpa:
    properties:
      hibernate:
        default_schema: auth_schema
    hibernate:
      ddl-auto: validate

server:
  port: 8081

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}
```

---

#### Task 1.5 — Cấu trúc package auth-service

```
com.vdt.auth/
├── config/
│   ├── SecurityConfig.java        # Cấu hình Spring Security, permit /auth/login
│   └── AppConfig.java             # Bean RestTemplate, BCryptPasswordEncoder
├── controller/
│   ├── AuthController.java        # POST /login, POST /register
│   ├── UserController.java        # GET/PUT /users
│   └── DepartmentController.java  # GET/POST /departments
├── service/
│   ├── AuthService.java
│   ├── UserService.java
│   └── DepartmentService.java
├── repository/
│   ├── UserRepository.java
│   └── DepartmentRepository.java
├── entity/
│   ├── User.java
│   └── Department.java
├── dto/
│   ├── LoginRequest.java          # { email, password }
│   ├── LoginResponse.java         # { token, userId, role, email, departmentId }
│   ├── RegisterRequest.java
│   └── UserDto.java
└── security/
    ├── JwtUtil.java               # generateToken, validateToken, extractClaims
    └── JwtFilter.java             # OncePerRequestFilter — xác thực mọi request
```

---

#### Task 1.6 — JwtUtil

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    public String generateToken(User user) {
        // companyId: lấy từ user.companyId (MANAGER_COMPANY)
        //            hoặc user.department.companyId (USER, MANAGER_CENTER)
        //            hoặc null (ADMIN)
        Integer companyId = user.getCompanyId() != null
            ? user.getCompanyId()
            : (user.getDepartment() != null ? user.getDepartment().getCompanyId() : null);

        return Jwts.builder()
            .subject(user.getEmail())
            .claim("userId",       user.getId())
            .claim("role",         user.getRole().name())
            .claim("departmentId", user.getDepartmentId())   // null với MANAGER_COMPANY & ADMIN
            .claim("companyId",    companyId)                 // null với ADMIN
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

---

#### Task 1.7 — API cần implement trong auth-service

| Method | Endpoint | Mô tả | Quyền |
|--------|----------|-------|-------|
| `POST` | `/login` | Đăng nhập, nhận JWT (có `companyId` + `departmentId`) | Public |
| `POST` | `/register` | Tạo tài khoản với role mới | ADMIN |
| `GET` | `/users` | Danh sách user | ADMIN |
| `PUT` | `/users/{id}` | Cập nhật info, đổi role | ADMIN |
| `GET` | `/departments` | Danh sách Trung tâm | ADMIN, MANAGER_COMPANY, MANAGER_CENTER |
| `POST` | `/departments` | Tạo Trung tâm | ADMIN |
| `GET` | `/companies` | Danh sách Công ty | ADMIN |
| `POST` | `/companies` | Tạo Công ty | ADMIN |
| `GET` | `/internal/users/{id}` | **[Internal]** Lấy thông tin user | Các service khác |
| `GET` | `/internal/manager/center/{deptId}` | **[Internal]** Lấy MANAGER_CENTER của Trung tâm | scheduler-service |
| `GET` | `/internal/manager/company/{companyId}` | **[Internal]** Lấy MANAGER_COMPANY của Công ty | scheduler-service |
| `GET` | `/internal/admin` | **[Internal]** Lấy email ADMIN | scheduler-service |

> **Test:** Dùng Postman `POST /api/auth/login` → nhận token → paste vào [jwt.io](https://jwt.io) để verify claims có đủ `userId`, `role`, `departmentId`.

---

### Ngày 3 (24/06) — auth-service dockerize + CI/CD setup

#### Task 1.CI — GitHub Actions CI pipeline

Tạo file `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]

jobs:
  build-test:
    name: Build & Test — Backend Services
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    env:
      SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/testdb
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      JWT_SECRET: ci-test-secret-key-for-github-actions-only

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Build auth-service
        run: mvn -f auth-service/pom.xml package -DskipTests -B

      - name: Build document-service
        run: mvn -f document-service/pom.xml package -DskipTests -B

      - name: Build notification-service
        run: mvn -f notification-service/pom.xml package -DskipTests -B

      - name: Build scheduler-service
        run: mvn -f scheduler-service/pom.xml package -DskipTests -B

  docker-build:
    name: Docker Build — Verify Images
    runs-on: ubuntu-latest
    needs: build-test

    steps:
      - uses: actions/checkout@v4

      - name: Build all Docker images
        run: docker compose build
```

**Lưu ý quan trọng:**
- Phase 1 (Day 3): dùng `-DskipTests` vì chưa có test — chỉ verify compile thành công
- Phase 2 (Day 9): bỏ `-DskipTests` sau khi viết một số unit test cơ bản
- `JWT_SECRET` trong CI là giá trị fake, không phải secret thật → OK để commit

**Dockerfile multi-stage (dùng chung cho cả 4 service):**

```dockerfile
# Bước 1: build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -B

# Bước 2: runtime image nhỏ gọn
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> `.dockerignore` cho mỗi service:
> ```
> target/
> .mvn/
> *.md
> .git
> ```

**README.md badge** (thêm vào đầu file):

```markdown
[![CI](https://github.com/<username>/VDT-MiniProject/actions/workflows/ci.yml/badge.svg)](https://github.com/<username>/VDT-MiniProject/actions/workflows/ci.yml)
```

---

### Ngày 10 (01/07) — Angular scaffold + Login

> **Lưu ý v2.0:** Angular scaffold chuyển sang Ngày 10 (01/07) để tập trung backend hoàn chỉnh trước. Nội dung không thay đổi.

#### Task 1.8 — Tạo Angular project

```bash
ng new frontend --routing --style=scss --skip-tests
cd frontend
ng add @angular/material

# Tạo các module chính
ng generate module core --module app
ng generate module shared --module app
ng generate module features/auth --route auth --module app
ng generate module features/documents --route documents --module app
ng generate module features/notifications --route notifications --module app
ng generate module features/dashboard --route dashboard --module app
ng generate module features/admin --route admin --module app
```

#### Task 1.9 — Cấu trúc Angular

```
src/app/
├── core/
│   ├── interceptors/
│   │   └── auth.interceptor.ts     # Gắn JWT vào mọi request, handle 401
│   ├── guards/
│   │   ├── auth.guard.ts           # CanActivate: kiểm tra token còn hạn
│   │   └── role.guard.ts           # CanActivate: kiểm tra role có quyền
│   └── services/
│       └── auth.service.ts         # login(), logout(), getCurrentUser(), hasRole()
├── shared/
│   └── models/
│       ├── user.model.ts
│       ├── document.model.ts
│       └── alert-log.model.ts
└── features/
    ├── auth/
    │   └── login/login.component.ts
    ├── documents/
    │   ├── document-list/
    │   ├── document-form/
    │   └── document-detail/
    ├── notifications/
    │   └── alert-log/
    ├── dashboard/
    └── admin/
        ├── user-management/
        └── alert-config/
```

#### Task 1.10 — AuthInterceptor

```typescript
// core/interceptors/auth.interceptor.ts
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getToken();

  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        authService.logout();
        router.navigate(['/auth/login']);
      }
      return throwError(() => err);
    })
  );
};
```

#### Task 1.11 — AuthService

```typescript
// core/services/auth.service.ts
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private currentUser = signal<UserInfo | null>(null);

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password })
      .pipe(tap(res => {
        localStorage.setItem('token', res.token);
        this.currentUser.set({
          userId: res.userId,
          email: res.email,
          role: res.role,
          departmentId: res.departmentId
        });
      }));
  }

  logout() {
    localStorage.removeItem('token');
    this.currentUser.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  hasRole(...roles: string[]): boolean {
    return roles.includes(this.currentUser()?.role ?? '');
  }

  isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) return false;
    // Kiểm tra expiry từ JWT payload (không cần gọi server)
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch { return false; }
  }
}
```

---

### Ngày 5 (21/06) — Buffer + test tuần 1

#### Task 1.12 — Checklist cuối tuần 1

- [ ] `docker-compose up` chạy thành công tất cả container (postgres, mailhog, nginx, auth-service)
- [ ] `POST /api/auth/login` trả JWT hợp lệ
- [ ] JWT có đủ claims: `userId`, `role`, `departmentId`
- [ ] Angular login page gọi được API, lưu token vào localStorage
- [ ] Route guard chặn `/documents` khi chưa đăng nhập, redirect về `/auth/login`
- [ ] 3 role khác nhau redirect về đúng trang sau login

---

## 3. Tuần 2 (22/06 – 28/06)

**document-service + notification-service + scheduler-service + Frontend core**

> **Mục tiêu cuối tuần:** Tạo được văn bản, nộp duyệt, Manager duyệt, cron job gửi email test, xem alert log trên UI.

---

### Ngày 6–7 (22–23/06) — document-service

#### Task 2.1 — Flyway migration `V1__init_document_schema.sql`

Đặt tại `document-service/src/main/resources/db/migration/`:

```sql
CREATE SCHEMA IF NOT EXISTS document_schema;
SET search_path TO document_schema;

CREATE TABLE documents (
    id            SERIAL PRIMARY KEY,
    code          VARCHAR(50)  UNIQUE NOT NULL,
    title         VARCHAR(255) NOT NULL,
    type          VARCHAR(50)  NOT NULL
                  CHECK (type IN ('CONTRACT', 'LICENSE', 'CERTIFICATE', 'SR')),
    level         VARCHAR(20)  NOT NULL DEFAULT 'CENTER'
                  CHECK (level IN ('CENTER', 'COMPANY', 'GROUP')),
    -- Cấp văn bản: CENTER (Trung tâm), COMPANY (Công ty), GROUP (Tập đoàn)
    -- Quyết định ai được duyệt và ai nhận cảnh báo
    issue_date    DATE,
    expiry_date   DATE NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    -- Trạng thái: DRAFT, PENDING, ACTIVE, REJECTED, WARNING, EXPIRED, CANCELLED
    file_path     VARCHAR(500),
    note          TEXT,
    owner_id      INTEGER NOT NULL,      -- tham chiếu auth_schema.users.id (không FK)
    department_id INTEGER NOT NULL,      -- tham chiếu auth_schema.departments.id (Trung tâm tạo văn bản)
    company_id    INTEGER NOT NULL,      -- tham chiếu auth_schema.companies.id (lấy từ JWT khi tạo)
    renewal_count INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

CREATE TABLE approval_requests (
    id           SERIAL PRIMARY KEY,
    document_id  INTEGER REFERENCES documents(id),
    requested_by INTEGER NOT NULL,
    reviewed_by  INTEGER,
    action       VARCHAR(20) NOT NULL,
    -- Hành động: SUBMIT, APPROVE, REJECT, RENEW, CANCEL
    comment      TEXT,
    requested_at TIMESTAMP DEFAULT NOW(),
    reviewed_at  TIMESTAMP
);

-- Index để cron job và filter theo vai trò query nhanh
CREATE INDEX idx_documents_expiry   ON documents(expiry_date) WHERE status IN ('ACTIVE', 'WARNING');
CREATE INDEX idx_documents_status   ON documents(status);
CREATE INDEX idx_documents_owner    ON documents(owner_id);
CREATE INDEX idx_documents_dept     ON documents(department_id);
CREATE INDEX idx_documents_company  ON documents(company_id);
CREATE INDEX idx_documents_level    ON documents(level);

-- ─── Outbox table (Transactional Outbox Pattern) ───
-- Lưu event cần gửi cho notification-service trong cùng transaction đổi status văn bản
CREATE TABLE notification_outbox (
    id          SERIAL PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    -- Loại: APPROVAL_REQUEST (→ MANAGER), APPROVED (→ USER), REJECTED (→ USER)
    document_id INTEGER      NOT NULL,
    payload     JSONB        NOT NULL,
    -- JSON: {docId, docCode, docTitle, expiryDate, recipientEmail, recipientRole, ...}
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING → SENT | FAILED (sau 3 lần retry)
    retry_count INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    DEFAULT NOW(),
    sent_at     TIMESTAMP
);

-- Partial index: chỉ quét PENDING, bỏ qua SENT/FAILED đã xử lý
CREATE INDEX idx_outbox_pending ON notification_outbox(created_at)
    WHERE status = 'PENDING';
```

---

#### Task 2.2 — JWT verification trong document-service

Document-service **không gọi auth-service** để verify JWT — dùng chung `JWT_SECRET` để tự parse:

```java
// Giống JwtUtil trong auth-service nhưng bỏ method generateToken
// Chỉ cần: extractClaims() và isValid()

// Trong JwtFilter, sau khi parse JWT:
String userId       = claims.get("userId",       Integer.class).toString();
String role         = claims.get("role",          String.class);
Integer departmentId = claims.get("departmentId", Integer.class);   // null với MANAGER_COMPANY & ADMIN
Integer companyId    = claims.get("companyId",    Integer.class);   // null với ADMIN

// Đặt vào SecurityContext để các controller dùng
UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
);
auth.setDetails(Map.of(
    "role",         role,
    "departmentId", departmentId != null ? departmentId.toString() : "",
    "companyId",    companyId    != null ? companyId.toString()    : ""
));
SecurityContextHolder.getContext().setAuthentication(auth);
```

---

#### Task 2.3 — Approval workflow (logic chuyển trạng thái)

Đặt trong `DocumentService.java`:

```java
// Không còn gọi notificationClient trực tiếp.
// Thay vào đó: viết vào notification_outbox trong cùng @Transactional.
// OutboxRelayJob sẽ đọc và gọi notification-service sau.

@Transactional
public Document submit(Long docId, Long requesterId, String managerEmail) {
    Document doc = findById(docId);
    if (!doc.getOwnerId().equals(requesterId)) throw new ForbiddenException();
    if (doc.getStatus() != DocumentStatus.DRAFT) throw new BusinessException("Chỉ DRAFT mới nộp được");

    doc.setStatus(DocumentStatus.PENDING);
    saveApprovalLog(docId, requesterId, "SUBMIT", null);
    docRepo.save(doc);

    // Outbox: atomic với transaction trên
    outboxRepo.save(NotificationOutbox.builder()
        .eventType("APPROVAL_REQUEST")
        .documentId(docId)
        .payload(buildPayload(doc, managerEmail, "MANAGER_CENTER"))
        .build());

    return doc;
}

@Transactional
public Document approve(Long docId, Long reviewerId, String reviewerRole,
                        Integer reviewerDeptId, Integer reviewerCompanyId) {
    Document doc = findById(docId);
    validateApprovePermission(doc, reviewerId, reviewerRole, reviewerDeptId, reviewerCompanyId);

    doc.setStatus(DocumentStatus.ACTIVE);
    saveApprovalLog(docId, reviewerId, "APPROVE", null);
    docRepo.save(doc);

    // Outbox: atomic với transaction trên — nếu NOTIF down, entry chờ relay retry
    String ownerEmail = resolveOwnerEmail(doc.getOwnerId());
    outboxRepo.save(NotificationOutbox.builder()
        .eventType("APPROVED")
        .documentId(docId)
        .payload(buildPayload(doc, ownerEmail, "USER"))
        .build());

    return doc;
}

@Transactional
public Document reject(Long docId, Long reviewerId, String reviewerRole,
                       Integer reviewerDeptId, Integer reviewerCompanyId, String reason) {
    Document doc = findById(docId);
    validateApprovePermission(doc, reviewerId, reviewerRole, reviewerDeptId, reviewerCompanyId);
    if (doc.getStatus() != DocumentStatus.PENDING) throw new BusinessException();

    doc.setStatus(DocumentStatus.REJECTED);
    saveApprovalLog(docId, reviewerId, "REJECT", reason);
    docRepo.save(doc);

    String ownerEmail = resolveOwnerEmail(doc.getOwnerId());
    outboxRepo.save(NotificationOutbox.builder()
        .eventType("REJECTED")
        .documentId(docId)
        .payload(buildPayload(doc, ownerEmail, "USER", reason))
        .build());

    return doc;
}

// Validate approve permission theo level + self-approval rule
private void validateApprovePermission(Document doc, Long reviewerId, String role,
                                       Integer deptId, Integer companyId) {
    if (doc.getStatus() != DocumentStatus.PENDING) throw new BusinessException("Chỉ PENDING mới duyệt được");
    if (doc.getOwnerId().equals(reviewerId)) throw new ForbiddenException("Không tự duyệt văn bản của mình");
    switch (doc.getLevel()) {
        case "CENTER"  -> { if ("USER".equals(role)) throw new ForbiddenException(); }
        case "COMPANY" -> { if (!Set.of("MANAGER_COMPANY","ADMIN").contains(role)) throw new ForbiddenException(); }
        case "GROUP"   -> { if (!"ADMIN".equals(role)) throw new ForbiddenException(); }
    }
}

public Document renew(Long docId, LocalDate newExpiryDate, Long userId, String role, Integer deptId) {
    Document doc = findById(docId);
    if (newExpiryDate.isBefore(LocalDate.now())) throw new BusinessException("Ngày gia hạn phải sau hôm nay");
    if (!canRenew(doc, userId, role, deptId)) throw new ForbiddenException();

    doc.setExpiryDate(newExpiryDate);
    doc.setStatus(DocumentStatus.ACTIVE); // WARNING/EXPIRED → ACTIVE
    doc.setRenewalCount(doc.getRenewalCount() + 1); // tăng đếm số lần gia hạn
    saveApprovalLog(docId, userId, "RENEW", "Gia hạn lần " + doc.getRenewalCount() + " đến " + newExpiryDate);
    return docRepo.save(doc);
}
```

---

#### Task 2.3b — OutboxRelayJob (trong document-service)

```java
@Component
@Slf4j
public class OutboxRelayJob {

    @Autowired NotificationOutboxRepository outboxRepo;
    @Autowired NotificationClient notifClient;  // REST client gọi notification-service

    @Scheduled(fixedDelay = 10_000)  // mỗi 10 giây
    @Transactional
    public void relay() {
        // Chỉ lấy PENDING, tối đa 50 entry mỗi lần để không block lâu
        List<NotificationOutbox> pending = outboxRepo
            .findTop50ByStatusAndRetryCountLessThanOrderByCreatedAt("PENDING", 3);

        for (NotificationOutbox entry : pending) {
            try {
                notifClient.sendEmail(entry.getEventType(), entry.getPayload());
                entry.setStatus("SENT");
                entry.setSentAt(LocalDateTime.now());
                log.info("[Outbox] SENT id={} type={}", entry.getId(), entry.getEventType());
            } catch (Exception e) {
                entry.setRetryCount(entry.getRetryCount() + 1);
                if (entry.getRetryCount() >= 3) entry.setStatus("FAILED");
                log.warn("[Outbox] retry={} id={} err={}", entry.getRetryCount(), entry.getId(), e.getMessage());
            }
            outboxRepo.save(entry);
        }
    }
}
```

> **Lưu ý:** `@Transactional` trên relay đảm bảo nếu `outboxRepo.save` fail thì status không bị update sai. `fixedDelay` (chờ sau khi xong) thay vì `fixedRate` (tránh chạy chồng).

---

#### Task 2.4 — File upload

```java
@PostMapping("/{id}/upload")
public ResponseEntity<Document> uploadFile(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file,
        Authentication auth) {

    String ownerId = (String) auth.getPrincipal();
    Document doc = documentService.findById(id);

    // Validate: chỉ chấp nhận PDF và Word
    String contentType = file.getContentType();
    if (!List.of("application/pdf",
                 "application/msword",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .contains(contentType)) {
        return ResponseEntity.badRequest().build();
    }

    String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
    Path target = Paths.get("/app/uploads/" + filename);
    Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

    doc.setFilePath("/uploads/" + filename);
    return ResponseEntity.ok(docRepo.save(doc));
}
```

---

#### Task 2.5 — Internal endpoint cho scheduler

```java
// Chỉ gọi nội bộ từ scheduler-service, không qua Nginx
@GetMapping("/internal/documents/expiring")
public List<ExpiringDocDto> getExpiringDocs() {
    LocalDate today = LocalDate.now();
    LocalDate in30Days = today.plusDays(30);

    return docRepo.findByStatusInAndExpiryDateBetween(
            List.of(DocumentStatus.ACTIVE, DocumentStatus.WARNING),
            today.minusDays(1), // bao gồm cả hết hạn hôm nay (daysLeft = 0)
            in30Days
        ).stream()
        .map(doc -> {
            long daysLeft = ChronoUnit.DAYS.between(today, doc.getExpiryDate());
            UserInfo owner   = authClient.getUserById(doc.getOwnerId());
            UserInfo manager = authClient.getManagerByDeptId(doc.getDepartmentId());
            UserInfo admin   = authClient.getAdmin();
            return new ExpiringDocDto(doc, daysLeft, owner, manager, admin);
        })
        .collect(Collectors.toList());
}

@PatchMapping("/internal/documents/{id}/status")
public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
    documentService.updateStatusInternal(id, DocumentStatus.valueOf(body.get("status")));
    return ResponseEntity.ok().build();
}
```

---

### Ngày 8 (24/06) — notification-service

#### Task 2.6 — Flyway migration `V1__init_notification_schema.sql`

```sql
CREATE SCHEMA IF NOT EXISTS notification_schema;
SET search_path TO notification_schema;

CREATE TABLE alert_configs (
    id             SERIAL PRIMARY KEY,
    document_type  VARCHAR(50),          -- NULL = áp dụng cho tất cả loại
    document_level VARCHAR(20),          -- NULL = áp dụng cho tất cả cấp
    remind_days    VARCHAR(50) DEFAULT '30,15,7,1',
    is_active      BOOLEAN DEFAULT true
);

-- ─── Alert Queue (Outbox Pattern — phía Inbox của notification-service) ───
-- Scheduler ghi vào đây, AlertQueueProcessor xử lý async mỗi 30s
CREATE TABLE alert_queue (
    id           SERIAL PRIMARY KEY,
    document_id  INTEGER      NOT NULL,
    doc_level    VARCHAR(20)  NOT NULL,  -- CENTER / COMPANY / GROUP
    days_left    INTEGER      NOT NULL,
    payload      JSONB        NOT NULL,
    -- JSON: {docId, docCode, docTitle, expiryDate, level, daysLeft, recipients[{email, role}]}
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING → PROCESSING → DONE | FAILED
    retry_count  INTEGER      NOT NULL DEFAULT 0,
    queued_at    TIMESTAMP    DEFAULT NOW(),
    processed_at TIMESTAMP
);

CREATE INDEX idx_alert_queue_pending ON alert_queue(queued_at)
    WHERE status = 'PENDING';

CREATE TABLE alert_logs (
    id              SERIAL PRIMARY KEY,
    document_id     INTEGER NOT NULL,
    days_before     INTEGER NOT NULL,
    recipient_role  VARCHAR(20) NOT NULL,
    recipient_email VARCHAR(100) NOT NULL,
    sent_at         TIMESTAMP DEFAULT NOW(),
    status          VARCHAR(20) DEFAULT 'SENT'  -- SENT, FAILED
);

-- Chống gửi trùng trong cùng ngày
CREATE UNIQUE INDEX idx_alert_no_dup
    ON alert_logs(document_id, days_before, recipient_email, DATE(sent_at));

-- Seed cấu hình mặc định (áp dụng cho tất cả loại + cấp)
INSERT INTO alert_configs (document_type, document_level, remind_days, is_active)
VALUES (NULL, NULL, '30,15,7,1', true);
```

---

#### Task 2.7 — EmailService

```java
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@company.vn}")
    private String from;

    public void sendAlert(String to, String docTitle, String docCode,
                          LocalDate expiryDate, int daysLeft, String role) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(buildSubject(docTitle, daysLeft));
        helper.setText(buildHtmlBody(docTitle, docCode, expiryDate, daysLeft, role), true);

        mailSender.send(message);
    }

    private String buildSubject(String title, int daysLeft) {
        if (daysLeft <= 0) return "[HẾT HẠN] " + title;
        if (daysLeft == 1) return "[KHẨN] " + title + " hết hạn NGÀY MAI";
        return "[CẢNH BÁO] " + title + " sắp hết hạn trong " + daysLeft + " ngày";
    }

    private String buildHtmlBody(String title, String code, LocalDate expiry, int daysLeft, String role) {
        // Trả về HTML email đơn giản
        // Màu sắc: xanh (T-30/15), cam (T-7), đỏ (T-1), tím (hết hạn)
        String color = daysLeft <= 0 ? "#9C27B0" : daysLeft == 1 ? "#f44336" : daysLeft <= 7 ? "#FF9800" : "#4CAF50";
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px;">
                <div style="background:%s; color:white; padding:16px; border-radius:4px 4px 0 0">
                    <h2 style="margin:0">%s</h2>
                </div>
                <div style="padding:16px; border:1px solid #eee;">
                    <p>Kính gửi <strong>%s</strong>,</p>
                    <p>Văn bản <strong>%s</strong> (Mã: %s) sẽ hết hạn vào ngày <strong>%s</strong>.</p>
                    <p>%s</p>
                    <p>Vui lòng đăng nhập hệ thống để gia hạn hoặc xử lý.</p>
                </div>
            </div>
            """.formatted(color, buildSubject(title, daysLeft), role, title, code, expiry,
                          daysLeft <= 0 ? "Văn bản ĐÃ HẾT HẠN. Cần xử lý ngay." :
                          "Còn " + daysLeft + " ngày.");
    }
}
```

---

#### Task 2.8 — AlertService (xử lý gửi, giữ nguyên logic dedup)

```java
@Service
public class AlertService {

    @Autowired AlertLogRepository alertLogRepo;
    @Autowired EmailService emailService;

    // Được gọi bởi AlertQueueProcessor — không còn gọi trực tiếp từ scheduler
    @Transactional
    public void processAlert(AlertQueuePayload payload) {
        for (RecipientInfo recipient : payload.getRecipients()) {
            boolean alreadySent = alertLogRepo.existsByDocumentIdAndDaysBeforeAndRecipientEmailAndSentAtDate(
                payload.getDocId(), payload.getDaysLeft(), recipient.getEmail(), LocalDate.now()
            );
            if (!alreadySent) {
                try {
                    emailService.sendAlert(recipient.getEmail(), payload.getDocTitle(),
                        payload.getDocCode(), payload.getExpiryDate(),
                        payload.getDaysLeft(), recipient.getRole());
                    alertLogRepo.save(AlertLog.success(payload, recipient));
                } catch (Exception e) {
                    alertLogRepo.save(AlertLog.failed(payload, recipient));
                    log.error("Gửi email thất bại tới {}: {}", recipient.getEmail(), e.getMessage());
                }
            }
        }
    }
}
```

---

#### Task 2.8b — AlertQueueProcessor (trong notification-service)

```java
@Component
@Slf4j
public class AlertQueueProcessor {

    @Autowired AlertQueueRepository queueRepo;
    @Autowired AlertService alertService;
    @Autowired DocumentClient documentClient;  // để PATCH status EXPIRED/WARNING

    @Scheduled(fixedDelay = 30_000)  // mỗi 30 giây
    @Transactional
    public void process() {
        List<AlertQueue> pending = queueRepo
            .findTop100ByStatusAndRetryCountLessThanOrderByQueuedAt("PENDING", 3);

        for (AlertQueue item : pending) {
            try {
                AlertQueuePayload payload = parsePayload(item);
                alertService.processAlert(payload);

                // Cập nhật status văn bản nếu cần
                if (item.getDaysLeft() <= 0) {
                    documentClient.updateStatus(item.getDocumentId(), "EXPIRED");
                } else if (item.getDaysLeft() == 7) {
                    documentClient.updateStatus(item.getDocumentId(), "WARNING");
                }

                item.setStatus("DONE");
                item.setProcessedAt(LocalDateTime.now());
                log.info("[AlertQueue] DONE id={} docId={}", item.getId(), item.getDocumentId());

            } catch (Exception e) {
                item.setRetryCount(item.getRetryCount() + 1);
                if (item.getRetryCount() >= 3) item.setStatus("FAILED");
                log.warn("[AlertQueue] retry={} id={} err={}", item.getRetryCount(), item.getId(), e.getMessage());
            }
            queueRepo.save(item);
        }
    }
}
```

#### Task 2.8c — AlertSchedulingService (cron trong notification-service)

> **v1.6:** Logic cron chuyển vào notification-service. notification-service tự pull document-service → ghi thẳng `alert_queue` trong cùng DB (không qua REST) → true outbox. scheduler-service chỉ còn làm proxy manual trigger.

```java
@Service
@Slf4j
public class AlertSchedulingService {

    @Autowired AlertQueueRepository queueRepo;
    @Autowired AlertConfigRepository configRepo;
    @Autowired DocumentClient documentClient;    // gọi document-service: getExpiringDocs
    @Autowired AuthClient authClient;            // gọi auth-service: lấy email manager/admin

    // Production: chạy lúc 8:00 sáng mỗi ngày
    @Scheduled(cron = "0 0 8 * * *")
    public void dailyCheck() {
        runCheck();
    }

    // Manual trigger: gọi trực tiếp, POST /internal/trigger trỏ vào đây
    public void runCheck() {
        log.info("=== [Alert] Daily check bắt đầu ===");
        List<ExpiringDocDto> docs = documentClient.getExpiringDocs();
        int[] remindDays = configRepo.findActiveRemindDays();  // [30,15,7,1]

        int enqueued = 0;
        for (ExpiringDocDto doc : docs) {
            try {
                long daysLeft = doc.getDaysLeft();
                boolean shouldAlert = daysLeft <= 0 ||
                    Arrays.stream(remindDays).anyMatch(d -> d == daysLeft);

                if (shouldAlert) {
                    List<RecipientInfo> recipients = resolveRecipients(doc, daysLeft);
                    // Ghi thẳng vào alert_queue — cùng DB, cùng service → không mất event
                    queueRepo.save(AlertQueue.builder()
                        .documentId(doc.getId())
                        .docLevel(doc.getLevel())
                        .daysLeft((int) daysLeft)
                        .payload(buildPayload(doc, recipients))
                        .status("PENDING")
                        .build());
                    enqueued++;
                }
            } catch (Exception e) {
                log.error("[Alert] docId={}: {}", doc.getId(), e.getMessage());
            }
        }
        log.info("=== [Alert] Enqueued {} / {} ===", enqueued, docs.size());
    }

    private List<RecipientInfo> resolveRecipients(ExpiringDocDto doc, long daysLeft) {
        List<RecipientInfo> list = new ArrayList<>();
        list.add(new RecipientInfo(doc.getOwnerEmail(), "USER"));  // luôn gửi
        switch (doc.getLevel()) {
            case "CENTER" -> {
                if (daysLeft <= 7)
                    list.add(new RecipientInfo(authClient.getCenterMgrEmail(doc.getDepartmentId()), "MANAGER_CENTER"));
                if (daysLeft <= 1)
                    list.add(new RecipientInfo(authClient.getCompanyMgrEmail(doc.getCompanyId()), "MANAGER_COMPANY"));
                if (daysLeft <= 0)
                    list.add(new RecipientInfo(authClient.getAdminEmail(), "ADMIN"));
            }
            case "COMPANY" -> {
                if (daysLeft <= 15)
                    list.add(new RecipientInfo(authClient.getCompanyMgrEmail(doc.getCompanyId()), "MANAGER_COMPANY"));
                if (daysLeft <= 7)
                    list.add(new RecipientInfo(authClient.getAdminEmail(), "ADMIN"));
            }
            case "GROUP" -> {
                list.add(new RecipientInfo(authClient.getAdminEmail(), "ADMIN"));  // luôn gửi ADMIN
            }
        }
        return list;
    }
}
```

**Endpoint manual trigger trong notification-service:**

```java
@RestController
public class InternalTriggerController {

    @Autowired AlertSchedulingService schedulingService;

    @PostMapping("/internal/trigger")
    public ResponseEntity<String> trigger() {
        schedulingService.runCheck();
        return ResponseEntity.ok("Alert check triggered");
    }
}
```

---

### Ngày 9 (30/06) — scheduler-service (thin proxy)

> **v1.6:** scheduler-service không còn business logic. Chỉ là proxy HTTP để trigger thủ công cho dev/test. Không cần DocumentClient hay NotificationClient phức tạp.

#### Task 2.9 — Trigger proxy

```java
@RestController
@Slf4j
public class TriggerController {

    @Autowired NotificationClient notificationClient;  // Feign/RestTemplate → notification-service

    // Gọi: POST http://localhost:8084/internal/trigger
    @PostMapping("/internal/trigger")
    public ResponseEntity<String> triggerManually() {
        log.info("[Scheduler] Manual trigger → notification-service");
        notificationClient.triggerDailyCheck();  // POST /internal/trigger on port 8083
        return ResponseEntity.ok("Forwarded to notification-service");
    }
}
```

```java
// NotificationClient.java
@FeignClient(name = "notification-service", url = "${services.notification.url}")
public interface NotificationClient {
    @PostMapping("/internal/trigger")
    void triggerDailyCheck();
}
```

**application.yml của scheduler-service:**
```yaml
services:
  notification:
    url: http://notification-service:8083

spring:
  main:
    web-application-type: reactive  # hoặc servlet — không cần DB, không cần data source
```

**Không cần:**
- Không có DB / Flyway
- Không có business logic
- Không có `@Scheduled` (notification-service đã tự chạy cron)

---

### Ngày 10–11 (26–27/06) — Frontend core

#### Task 2.10 — Document List component

```typescript
@Component({
  selector: 'app-document-list',
  template: `
    <div class="toolbar">
      <mat-form-field>
        <mat-select [(value)]="statusFilter" (selectionChange)="loadDocuments()">
          <mat-option value="">Tất cả trạng thái</mat-option>
          <mat-option value="DRAFT">Nháp</mat-option>
          <mat-option value="PENDING">Chờ duyệt</mat-option>
          <mat-option value="ACTIVE">Đang hiệu lực</mat-option>
          <mat-option value="WARNING">Sắp hết hạn</mat-option>
          <mat-option value="EXPIRED">Hết hạn</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-raised-button color="primary" routerLink="/documents/new">+ Tạo văn bản</button>
    </div>

    <mat-table [dataSource]="documents">
      <ng-container matColumnDef="code">
        <mat-header-cell *matHeaderCellDef>Mã</mat-header-cell>
        <mat-cell *matCellDef="let doc">{{ doc.code }}</mat-cell>
      </ng-container>

      <ng-container matColumnDef="status">
        <mat-header-cell *matHeaderCellDef>Trạng thái</mat-header-cell>
        <mat-cell *matCellDef="let doc">
          <mat-chip [style.background]="getStatusColor(doc.status)">
            {{ getStatusLabel(doc.status) }}
          </mat-chip>
        </mat-cell>
      </ng-container>

      <ng-container matColumnDef="expiryDate">
        <mat-header-cell *matHeaderCellDef>Hết hạn</mat-header-cell>
        <mat-cell *matCellDef="let doc" [class.text-red]="isExpiringSoon(doc)">
          {{ doc.expiryDate | date:'dd/MM/yyyy' }}
          <span *ngIf="doc.daysLeft >= 0"> (còn {{ doc.daysLeft }} ngày)</span>
        </mat-cell>
      </ng-container>
    </mat-table>
  `
})
export class DocumentListComponent {
  statusFilter = '';
  documents: Document[] = [];

  getStatusColor(status: string): string {
    const colors: Record<string, string> = {
      DRAFT: '#9E9E9E', PENDING: '#FF9800', ACTIVE: '#4CAF50',
      REJECTED: '#f44336', WARNING: '#FF6F00', EXPIRED: '#9C27B0', CANCELLED: '#607D8B'
    };
    return colors[status] ?? '#9E9E9E';
  }

  isExpiringSoon(doc: Document): boolean {
    return doc.daysLeft !== undefined && doc.daysLeft <= 7 && doc.daysLeft >= 0;
  }
}
```

---

#### Task 2.11 — Document Form (tạo/sửa)

```typescript
// Reactive Form với validation
form = this.fb.group({
  title:      ['', [Validators.required, Validators.maxLength(255)]],
  type:       ['', Validators.required],
  issueDate:  [null as Date | null],
  expiryDate: [null as Date | null, [Validators.required, this.futureDateValidator()]],
  note:       ['']
});

// Custom validator: ngày hết hạn phải sau hôm nay
futureDateValidator(): ValidatorFn {
  return (control) => {
    if (!control.value) return null;
    const selected = new Date(control.value);
    return selected > new Date() ? null : { pastDate: true };
  };
}

// Submit form
onSubmit() {
  if (this.form.invalid) return;
  const payload = this.form.value;
  const request$ = this.isEdit
    ? this.docService.update(this.docId!, payload)
    : this.docService.create(payload);

  request$.subscribe({
    next: () => this.router.navigate(['/documents']),
    error: (err) => this.snackBar.open(err.error?.message ?? 'Lỗi!', 'Đóng')
  });
}
```

---

#### Task 2.12 — Approval UI (hiển thị theo role)

```typescript
// Trong DocumentDetailComponent
canApprove(doc: Document): boolean {
  return this.auth.hasRole('MANAGER', 'ADMIN') && doc.status === 'PENDING';
}

canSubmit(doc: Document): boolean {
  return doc.status === 'DRAFT' && doc.ownerId === this.auth.getCurrentUser()?.userId;
}

canRenew(doc: Document): boolean {
  const status = ['ACTIVE', 'WARNING', 'EXPIRED'];
  if (!status.includes(doc.status)) return false;
  if (this.auth.hasRole('ADMIN', 'MANAGER')) return true;
  return doc.ownerId === this.auth.getCurrentUser()?.userId;
}

openRejectDialog(doc: Document) {
  const dialogRef = this.dialog.open(RejectDialogComponent);
  dialogRef.afterClosed().subscribe(reason => {
    if (reason) this.docService.reject(doc.id, reason).subscribe(() => this.loadDoc());
  });
}
```

---

### Ngày 12 (28/06) — Buffer + integration test tuần 2

#### Task 2.13 — Checklist cuối tuần 2

- [ ] Tạo văn bản → lưu DRAFT thành công
- [ ] Upload file đính kèm PDF/Word → lưu đúng path
- [ ] Nộp duyệt → status PENDING, Manager nhận email (kiểm tra `http://localhost:8025`)
- [ ] Manager duyệt → status ACTIVE, User nhận email xác nhận
- [ ] Manager từ chối + lý do → User nhận email có lý do từ chối
- [ ] Gọi `POST /internal/trigger` trên scheduler → văn bản test hết hạn 7 ngày → gửi email User + Manager
- [ ] Alert log hiển thị đúng trên UI, không gửi trùng khi trigger 2 lần cùng ngày
- [ ] Filter văn bản theo status hoạt động đúng theo role

---

## 4. Tuần 3 (29/06 – 05/07)

**Dashboard + Gia hạn + Polish + Demo**

> **Mục tiêu cuối tuần:** Hệ thống hoàn chỉnh, demo được, có báo cáo và slide.

---

### Ngày 13 (29/06) — Dashboard + Gia hạn

#### Task 3.1 — Dashboard API

Đặt trong notification-service, endpoint `/api/notifications/dashboard/stats`:

```java
@GetMapping("/dashboard/stats")
public DashboardStats getStats(Authentication auth) {
    String role = extractRole(auth);
    Integer deptId = extractDeptId(auth);

    // Filter theo role: ADMIN xem tất cả, MANAGER/USER xem phòng mình
    List<Document> docs = documentClient.getAllDocuments(role, deptId);

    return DashboardStats.builder()
        .total(docs.size())
        .active(count(docs, "ACTIVE"))
        .warning(count(docs, "WARNING"))
        .expired(count(docs, "EXPIRED"))
        .pending(count(docs, "PENDING"))
        .expiringIn30Days(
            docs.stream()
               .filter(d -> d.getDaysLeft() != null && d.getDaysLeft() >= 0 && d.getDaysLeft() <= 30)
               .sorted(Comparator.comparing(Document::getExpiryDate))
               .limit(10)
               .map(this::toExpiringItem)
               .collect(Collectors.toList())
        )
        .build();
}
```

Response JSON:
```json
{
  "total": 45,
  "active": 20,
  "warning": 8,
  "expired": 5,
  "pending": 7,
  "expiringIn30Days": [
    { "id": 1, "title": "Hợp đồng ABC", "expiryDate": "2026-07-10", "daysLeft": 23, "type": "CONTRACT" }
  ]
}
```

---

#### Task 3.2 — Dashboard Angular component

```html
<!-- 4 stat cards -->
<div class="stat-grid">
  <mat-card class="stat-card">
    <mat-card-content>
      <div class="stat-number">{{ stats.active }}</div>
      <div class="stat-label">Đang hiệu lực</div>
    </mat-card-content>
  </mat-card>
  <mat-card class="stat-card warning">
    <mat-card-content>
      <div class="stat-number">{{ stats.warning }}</div>
      <div class="stat-label">Sắp hết hạn</div>
    </mat-card-content>
  </mat-card>
  <!-- ... expired, pending -->
</div>

<!-- Bảng sắp hết hạn -->
<mat-table [dataSource]="stats.expiringIn30Days">
  <ng-container matColumnDef="daysLeft">
    <mat-header-cell *matHeaderCellDef>Còn lại</mat-header-cell>
    <mat-cell *matCellDef="let item">
      <mat-chip [color]="item.daysLeft <= 7 ? 'warn' : 'primary'">
        {{ item.daysLeft }} ngày
      </mat-chip>
    </mat-cell>
  </ng-container>
</mat-table>
```

---

#### Task 3.3 — Renew Dialog

```typescript
// RenewDialogComponent
renewForm = this.fb.group({
  newExpiryDate: [null, [Validators.required, this.futureDateValidator()]]
});

confirm() {
  if (this.renewForm.invalid) return;
  this.docService.renew(this.data.docId, this.renewForm.value.newExpiryDate!)
    .subscribe({
      next: () => this.dialogRef.close(true),
      error: (err) => this.error = err.error?.message
    });
}
```

---

### Ngày 14 (30/06) — Error handling + UI polish

#### Task 3.4 — Global exception handler trong mỗi service

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(e.getMessage(), "BUSINESS_ERROR"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("Bạn không có quyền thực hiện hành động này", "FORBIDDEN"));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(e.getMessage(), "NOT_FOUND"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Lỗi hệ thống, vui lòng thử lại sau", "INTERNAL_ERROR"));
    }
}
```

---

#### Task 3.5 — Loading states và empty states

```html
<!-- Loading spinner -->
<div *ngIf="loading" class="loading-container">
  <mat-progress-spinner mode="indeterminate"></mat-progress-spinner>
</div>

<!-- Empty state -->
<div *ngIf="!loading && dataSource.data.length === 0" class="empty-state">
  <mat-icon style="font-size:64px; color:#ccc">description</mat-icon>
  <p>Chưa có văn bản nào</p>
  <button mat-raised-button color="primary" routerLink="/documents/new">
    Tạo văn bản đầu tiên
  </button>
</div>
```

---

#### Task 3.6 — Alert Log page

Bảng lịch sử với các cột:

| Cột | Mô tả |
|-----|-------|
| Tên văn bản | Link đến chi tiết |
| Ngưỡng | T-30 / T-15 / T-7 / T-1 / Hết hạn |
| Người nhận | Email |
| Role | USER / MANAGER / ADMIN |
| Thời gian gửi | `dd/MM/yyyy HH:mm` |
| Trạng thái | SENT (xanh) / FAILED (đỏ) |

Filter: theo phòng (MANAGER/ADMIN), theo khoảng ngày.

---

### Ngày 15 (01/07) — Kiểm thử end-to-end

#### Task 3.7 — Seed data cho test

Chạy script SQL để tạo dữ liệu test:

```sql
-- Tạo user test
INSERT INTO auth_schema.users (email, password_hash, full_name, role, department_id) VALUES
    ('user1@company.vn',    '$2a$10$...', 'Nguyễn Văn A',  'USER',    1),
    ('user2@company.vn',    '$2a$10$...', 'Lê Thị C',      'USER',    1),
    ('manager1@company.vn', '$2a$10$...', 'Trần Thị B',    'MANAGER', 1),
    ('manager2@company.vn', '$2a$10$...', 'Phạm Văn D',    'MANAGER', 2);

-- Tạo văn bản test với các ngưỡng khác nhau
INSERT INTO document_schema.documents (code, title, type, expiry_date, status, owner_id, department_id) VALUES
    ('DOC-T30',  'Hợp đồng test T-30',    'CONTRACT',    CURRENT_DATE + 30, 'ACTIVE',  2, 1),
    ('DOC-T15',  'Giấy phép test T-15',   'LICENSE',     CURRENT_DATE + 15, 'ACTIVE',  2, 1),
    ('DOC-T07',  'Chứng chỉ test T-7',    'CERTIFICATE', CURRENT_DATE + 7,  'ACTIVE',  2, 1),
    ('DOC-T01',  'SR khẩn test T-1',      'SR',          CURRENT_DATE + 1,  'ACTIVE',  2, 1),
    ('DOC-EXP',  'Văn bản đã hết hạn',    'REGULATION',  CURRENT_DATE - 1,  'ACTIVE',  2, 1);
```

---

#### Task 3.8 — Test checklist đầy đủ

**Luồng nghiệp vụ:**
- [ ] USER tạo văn bản → DRAFT
- [ ] USER upload file đính kèm
- [ ] USER nộp duyệt → PENDING, Manager nhận email
- [ ] MANAGER đăng nhập → xem danh sách PENDING → Duyệt → User nhận email xác nhận
- [ ] MANAGER từ chối + lý do → User nhận email có lý do
- [ ] User chỉnh sửa văn bản REJECTED → nộp lại
- [ ] USER gia hạn văn bản WARNING/EXPIRED → về ACTIVE

**Cron và cảnh báo:**
- [ ] Trigger `POST /internal/trigger` → email gửi đúng theo cấp (kiểm tra Mailhog)
- [ ] Trigger 2 lần cùng ngày → không gửi trùng
- [ ] Văn bản EXPIRED: status cập nhật đúng, Admin nhận email

**Phân quyền:**
- [ ] MANAGER không thấy văn bản phòng khác
- [ ] USER chỉ thấy văn bản của mình
- [ ] USER không duyệt được bất kỳ văn bản nào
- [ ] ADMIN thấy tất cả

**Dashboard:**
- [ ] Số liệu thống kê khớp với dữ liệu thực tế
- [ ] Bảng "sắp hết hạn 30 ngày" đúng
- [ ] Alert log hiển thị đủ lịch sử

---

### Ngày 16–17 (02–03/07) — Báo cáo + Slide

#### Task 3.9 — Cấu trúc báo cáo

1. **Giới thiệu bài toán** (1–2 trang): Bài toán thực tế, mục tiêu hệ thống
2. **Kiến trúc hệ thống** (2–3 trang): Diagram microservices, sơ đồ triển khai Docker
3. **Mô hình dữ liệu** (1–2 trang): ERD 3 schema, giải thích thiết kế không FK chéo schema
4. **Tính năng đã implement** (3–4 trang): Luồng duyệt, cảnh báo phân cấp, CRUD, upload
5. **Hướng dẫn cài đặt và chạy** (1 trang): 3 lệnh là chạy được
6. **Kết luận & hướng phát triển**: So sánh v1.0 vs roadmap v2.0/v3.0

---

#### Task 3.10 — Demo flow chuẩn bị sẵn

| Bước | Hành động | Kết quả kỳ vọng |
|------|-----------|-----------------|
| 1 | Đăng nhập admin | Vào dashboard tổng hợp |
| 2 | Đăng nhập user1 (tab mới) | Trang văn bản của mình |
| 3 | User tạo văn bản + upload PDF | Lưu DRAFT |
| 4 | User nộp duyệt | Mailhog nhận email cho Manager |
| 5 | Manager đăng nhập → duyệt | Mailhog nhận email cho User |
| 6 | Trigger cron thủ công | Mailhog nhận email alert cho văn bản test |
| 7 | User gia hạn văn bản WARNING | Status về ACTIVE |
| 8 | Admin xem dashboard | Số liệu thống kê đúng |

---

### Ngày 18–19 (04–05/07) — Buffer + Demo

#### Task 3.11 — Final checklist trước demo

- [ ] `docker-compose up --build` từ thư mục gốc chạy thành công, không lỗi
- [ ] Không có exception trong logs khi thực hiện các luồng chính
- [ ] Mailhog nhận email đúng nội dung, đúng người nhận
- [ ] UI không có lỗi console JavaScript
- [ ] README.md có hướng dẫn chạy (3 lệnh):
  ```bash
  git clone <repo>
  cd vdt-miniproject
  docker-compose up --build
  # Truy cập: http://localhost (frontend), http://localhost:8025 (Mailhog)
  ```

---

## 5. Bảng tóm tắt theo ngày

| Ngày | Thứ | Việc chính | Output kiểm tra |
|------|-----|-----------|-----------------|
| 17/06 | 3 | Docker Compose + Nginx + project scaffold | `docker-compose up` → postgres + mailhog green |
| 18/06 | 4 | auth-service: DB schema, Flyway, Entity, JWT | `POST /api/auth/login` trả JWT |
| 19/06 | 5 | auth-service: User/Dept API + Angular login page | Đăng nhập được, route guard hoạt động |
| 20/06 | 6 | Angular: interceptor, guard, layout, routing | 3 role redirect đúng trang |
| 21/06 | CN | Buffer + test + fix tuần 1 | Checklist tuần 1 xanh |
| 22/06 | 2 | document-service: DB schema, CRUD | `POST /api/documents` tạo DRAFT |
| 23/06 | 3 | document-service: approval workflow + upload | Submit → PENDING, Approve → ACTIVE |
| 24/06 | 4 | notification-service: email + alert config | Mailhog nhận email khi duyệt |
| 25/06 | 5 | scheduler-service: cron + REST clients | Trigger thủ công → email alert đúng |
| 26/06 | 6 | Frontend: Document list + form | Tạo/xem văn bản trên UI |
| 27/06 | CN | Frontend: Approval UI + alert log | Duyệt từ UI, xem alert log |
| 28/06 | 2 | Buffer + integration test tuần 2 | Checklist tuần 2 xanh |
| 29/06 | 3 | Dashboard API + Angular dashboard | Stat cards + bảng sắp hết hạn |
| 30/06 | 4 | Gia hạn + error handling + UI polish | Gia hạn WARNING → ACTIVE |
| 01/07 | 5 | E2E test với seed data đầy đủ | Checklist test đầy đủ xanh |
| 02/07 | 6 | Báo cáo phần kiến trúc + data model | Draft báo cáo 50% |
| 03/07 | CN | Slide demo + README hướng dẫn | Slide + README hoàn chỉnh |
| 04/07 | 2 | Buffer + rehearsal demo | Chạy demo trơn tru không lỗi |
| 05/07 | 3 | **DEMO + NỘP BÀI** ✅ | |

---

## 6. Lưu ý quan trọng cho solo developer

### Tránh các lỗi phổ biến

**1. Shared JWT_SECRET**
Đặt chung 1 biến env trong `docker-compose.yml`, tất cả service đọc từ đó. Không hardcode trong code.

**2. Flyway tự chạy khi service start**
Không cần chạy SQL thủ công. Mỗi service có thư mục riêng `resources/db/migration/`. Đặt đúng schema trong `application.yml`.

**3. Test cron không cần đợi 8 giờ sáng**
Thêm endpoint `POST /internal/trigger` trong scheduler để trigger thủ công. Xóa endpoint này trước khi nộp bài nếu cần.

**4. Gọi service khác fail không nên crash toàn bộ**
Dùng try-catch trong scheduler khi gọi notification-service. Nếu gửi email 1 văn bản lỗi, không nên dừng toàn bộ batch.

**5. Không dùng FK chéo schema**
`owner_id` trong `documents` chỉ lưu số nguyên, không có FOREIGN KEY sang `auth_schema.users`. Đây là thiết kế có chủ đích của microservices.

**6. Docker build chậm**
Thêm `.dockerignore` để loại bỏ `target/`, `node_modules/`. Dùng multi-stage Dockerfile cho Spring Boot và Angular để image nhỏ hơn.

### Thứ tự ưu tiên khi bị trễ tiến độ

Nếu không đủ thời gian, cắt giảm theo thứ tự này (giữ lại core):

| Ưu tiên | Giữ lại | Có thể bỏ |
|---------|---------|-----------|
| Bắt buộc | Login/JWT, CRUD văn bản, Approval workflow, Cron email | |
| Quan trọng | Dashboard, Alert log | Admin config ngưỡng |
| Nice-to-have | | Upload file, Renew dialog, UI polish |

### Công cụ hỗ trợ

| Công cụ | Dùng để |
|---------|---------|
| **Mailhog** `localhost:8025` | Xem email test không cần account email thật |
| **Postman** | Test API trước khi làm Frontend |
| **DBeaver / pgAdmin** | Xem trực tiếp dữ liệu trong PostgreSQL |
| **jwt.io** | Decode JWT để verify claims |
| `docker logs <container>` | Xem log service khi debug |

---

*Tài liệu này bổ sung cho [system-design-document-expiry-management.md](./system-design-document-expiry-management.md) và cung cấp hướng dẫn triển khai chi tiết theo từng ngày cho 1 người.*
