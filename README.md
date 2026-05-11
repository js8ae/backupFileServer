# Backup File Server

1,200개 이상의 병원으로부터 DB 덤프(zip)와 영상·이미지 파일을 수신·보관하는 내부 백업 파일 서버.

---

## 기술 스택

| 항목 | 선택 |
|---|---|
| 언어 | Java 21 LTS |
| 프레임워크 | Spring Boot 4.0.6 |
| Web | Spring MVC (Virtual Threads) |
| 청크 업로드 | TUS 1.0 (`tus-java-server:1.0.0-3.0`) |
| DB 접근 | JDBC + JdbcClient |
| DB | MariaDB |
| 마이그레이션 | Flyway |
| 보안 | Spring Security + JJWT 0.12.x |
| 모니터링 | Micrometer + Prometheus |
| API 문서 | springdoc-openapi 2.8.6 (Swagger UI) |

---

## 모듈 구조

```
backup-server/
├── domain/           # 도메인 모델·포트 인터페이스 — 외부 의존성 0
├── application/      # 유스케이스(Service) — Spring 의존 OK
├── api/              # 컨트롤러 (TUS, Auth, Admin)
├── infrastructure/   # 어댑터 (JDBC, Storage, TUS 라이브러리, JWT)
└── boot/             # @SpringBootApplication, application.yml, Flyway 마이그레이션
```

의존성 방향: `boot → api → application → domain`, `boot → infrastructure → application → domain`

---

## 핵심 기능

- **TUS 1.0 청크 업로드** — Stop / Resume 지원, 최대 50GB
- **SHA-256 무결성 검증** — 업로드 완료 시 자동 검증
- **병원별 쿼터 관리** — 저장 용량 초과 시 507 응답
- **라이선스 기간 검증** — 만료 병원 업로드 차단 (403)
- **배치 잡 3종**
  - `ExpiredSessionCleanupJob` — 만료 세션 ABORTED 처리 + 임시 파일 정리
  - `RetentionPolicyJob` — 보관 기간 초과 artifact 휴지통 이동 후 영구 삭제
  - `IntegrityVerificationJob` — 저장 파일 SHA-256 주기 재검증 (비트 부패 감지)

---

## 로컬 개발 환경 설정

### 사전 요구사항

- Java 21+
- Docker (Testcontainers용)
- MariaDB (로컬 실행 시)

### 1. 스토리지 디렉토리 생성

```bash
mkdir -p ~/backup-local/{incoming,artifacts,trash}
```

### 2. 로컬 설정 파일 작성

`boot/src/main/resources/application-local.yml` 생성 (`.gitignore`에 포함되어 있으므로 커밋되지 않음):

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/backup
    username: root
    password: your_password

backup:
  storage:
    incoming-root: ${HOME}/backup-local/incoming
    artifacts-root: ${HOME}/backup-local/artifacts
    trash-root: ${HOME}/backup-local/trash
  auth:
    jwt-secret: your-256-bit-or-longer-secret-key
    admin-key: your-admin-key
```

### 3. 애플리케이션 실행

```bash
./gradlew :boot:bootRun --args='--spring.profiles.active=local'
```

### 4. Swagger UI

```
http://localhost:8080/swagger-ui.html
```

JWT Bearer 인증과 X-Admin-Key 인증 스키마가 등록되어 있습니다.

---

## API 개요

### 인증

```
POST /auth/token
Content-Type: application/json

{ "clientId": "...", "clientSecret": "..." }
```

응답으로 받은 JWT를 이후 요청의 `Authorization: Bearer <token>` 헤더에 사용합니다. TTL: 15분.

### TUS 업로드 플로우

```
# 1. 업로드 세션 생성
POST /files
Upload-Type: DB | FILE
Upload-Filename: backup.zip
Upload-Length: <bytes>
Upload-Sha256: <sha256-hex>   (선택)
Tus-Resumable: 1.0.0

# 2. 청크 전송 (반복)
PATCH /files/{tusId}
Content-Type: application/offset+octet-stream
Upload-Offset: <offset>
Tus-Resumable: 1.0.0

# 3. Resume 시 offset 조회
HEAD /files/{tusId}
Tus-Resumable: 1.0.0

# 4. 업로드 중단
DELETE /files/{tusId}
Tus-Resumable: 1.0.0
```

### Admin API (X-Admin-Key 헤더 필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/admin/hospitals` | 병원 등록 |
| GET | `/admin/hospitals` | 병원 목록 조회 |
| PUT | `/admin/hospitals/{cocode}` | 병원 정보 수정 |
| POST | `/admin/hospitals/{cocode}/credentials` | 자격증명 발급 |
| GET | `/admin/hospitals/{cocode}/credentials` | 자격증명 목록 |
| DELETE | `/admin/hospitals/{cocode}/credentials/{clientId}` | 자격증명 폐기 |
| GET | `/admin/sessions` | 업로드 세션 조회 |

---

## 저장 레이아웃

```
{incoming-root}/
  uploads/{tusId}/
    data          ← 청크 누적 임시 파일
    info          ← TUS 메타데이터

{artifacts-root}/
  {cocode}/
    {type: db|file}/
      {yyyy}/{MM}/{dd}/
        {timestamp}_{filename}    ← 업로드 완료 후 영구 보관

{trash-root}/
  {yyyy-MM-dd}/
    {filename}    ← 보관 기간 만료 후 영구 삭제 전 대기
```

---

## DB 스키마

MariaDB, UTF-8mb4, 모든 시각은 UTC 기준 `DATETIME(6)`.

| 테이블 | 역할 |
|---|---|
| `hospital` | 병원 정보, 라이선스 기간, 최대 저장 용량 |
| `upload_session` | TUS 업로드 세션 (INITIATED → UPLOADING → COMPLETED / ABORTED / EXPIRED) |
| `backup_artifact` | 업로드 완료 파일 메타데이터 |
| `hospital_quota` | 병원별 사용 용량 |
| `hospital_credential` | 병원별 client_id / bcrypt 해시 |
| `backup_audit_log` | 이벤트 감사 로그 |

마이그레이션은 Flyway로 관리: `boot/src/main/resources/db/migration/V1__init.sql`

---

## 에러 코드

| 코드 | 의미 |
|---|---|
| 1000 | 병원 이미 존재 |
| 1001 | 병원 없음 |
| 1002 | 자격증명 없음 |
| 1003 | 업로드 세션 없음 |
| 1004 | 세션 접근 권한 없음 |
| 1005 | 저장 용량 초과 (507) |
| 1006 | 라이선스 만료 (403) |
| 1007 | SHA-256 무결성 불일치 (422) |
| 1008 | 요청 유효성 오류 |
| 1009 | 잘못된 인수 |
| 1010 | 서버 내부 오류 |

---

## 테스트

```bash
# 전체 테스트 (Testcontainers — Docker 필요)
./gradlew test

# 특정 모듈
./gradlew :domain:test
./gradlew :application:test
./gradlew :boot:test          # E2E 포함
```

총 64개 테스트, 모두 통과.

- **도메인 단위 테스트**: `HospitalTest`, `HospitalQuotaTest`
- **유스케이스 단위 테스트**: 10종 (Mockito)
- **통합 테스트**: `JdbcHospitalRepositoryIT`, `JdbcUploadSessionRepositoryIT` (Testcontainers MariaDB)
- **E2E 테스트**: `TusUploadE2EIT` — 실제 HTTP + TUS 프로토콜 전 플로우 검증

---

## 운영 배포

### 환경 변수 설정

```bash
cp deploy/systemd/backup-server.env.template /etc/backup-server/backup-server.env
chmod 600 /etc/backup-server/backup-server.env
# 실제 값 채워 넣기
```

```ini
DB_URL=jdbc:mariadb://db.internal:3306/backup
DB_USER=backup_app
DB_PASSWORD=<random>
BACKUP_JWT_SECRET=<256비트 이상 랜덤>
BACKUP_ADMIN_KEY=<admin key>
BACKUP_INCOMING_ROOT=/var/backup/incoming
BACKUP_ARTIFACTS_ROOT=/var/backup/artifacts
BACKUP_TRASH_ROOT=/var/backup/trash
```

### systemd 유닛 등록

```bash
cp deploy/systemd/backup-server.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now backup-server
```

서비스 옵션: ZGC GC, `MaxRAMPercentage=75`, `LimitNOFILE=65536`, `NoNewPrivileges`, `ProtectSystem=strict`

### Nginx 설정

```bash
cp deploy/nginx/backup-server.conf /etc/nginx/sites-available/backup-server
ln -s /etc/nginx/sites-available/backup-server /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

대용량 스트리밍을 위해 `client_max_body_size 0`, `proxy_request_buffering off`, `proxy_read_timeout 86400s` 적용.

### 스토리지 디렉토리 준비

```bash
useradd -r -s /sbin/nologin backup
mkdir -p /var/backup/{incoming,artifacts,trash}
chown -R backup:backup /var/backup
```

### 모니터링

`/actuator/prometheus` 엔드포인트를 Prometheus가 스크레이핑. 기존 Grafana 대시보드와 연동.  
해당 엔드포인트는 내부망(10.x, 172.16.x, 192.168.x)에서만 접근 가능.

---

## 보안 정책

- 병원별 `client_secret`은 bcrypt 해시만 DB에 저장 (평문 미보관)
- JWT HS256, TTL 15분
- TUS 모든 엔드포인트는 JWT 검증 + 세션 소유자 일치 검증 필수
- Admin API는 `X-Admin-Key` 헤더 검증
- 시크릿은 git 커밋 금지 — `application-local.yml`, `*.env` 파일은 `.gitignore`에 포함
