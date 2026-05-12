# Backup File Server — 클라이언트 연동 가이드

## 기본 정보

| 항목 | 값 |
|---|---|
| Base URL | `https://backup.intocns.com:8282` |
| 프로토콜 | TUS 1.0 (청크 업로드 / Stop·Resume) |
| 인증 | JWT Bearer (TTL 15분) |

---

## 전체 업로드 흐름

```
1. POST /auth/token          → JWT 발급
2. POST /files               → 업로드 세션 생성, TUS URI 획득
3. PATCH /files/{tusId}      → 파일 데이터 전송 (청크 반복)
   └─ 네트워크 끊김 시
      HEAD /files/{tusId}    → 현재 offset 조회
      PATCH /files/{tusId}   → offset부터 재개
4. (선택) DELETE /files/{tusId} → 업로드 중단
```

---

## 1. JWT 발급

### Request

```
POST /auth/token
Content-Type: application/json
```

```json
{
  "clientId": "발급받은 client_id",
  "clientSecret": "발급받은 client_secret"
}
```

### Response

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer"
}
```

| 항목 | 설명 |
|---|---|
| TTL | 발급 후 **15분** |
| 만료 시 | 재발급 필요 (`401` 응답) |

### 오류

| HTTP | code | 설명 |
|---|---|---|
| 400 | 1008 | 필수 필드 누락 (`clientId`, `clientSecret`) |
| 401 | — | clientId / clientSecret 불일치 |

---

## 2. 업로드 세션 생성

### Request

```
POST /files
Authorization: Bearer {accessToken}
Tus-Resumable: 1.0.0
Upload-Type: DB
Upload-Filename: backup_20260512.zip
Upload-Length: 104857600
Upload-Sha256: (선택) e3b0c44298fc1c149afb...
```

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Authorization` | ✅ | `Bearer {accessToken}` |
| `Tus-Resumable` | ✅ | 반드시 `1.0.0` 고정 |
| `Upload-Type` | ✅ | `DB` 또는 `FILE` |
| `Upload-Filename` | ✅ | 원본 파일명 |
| `Upload-Length` | ✅ | 전체 파일 크기 (bytes) |
| `Upload-Sha256` | ❌ | SHA-256 hex — 전달 시 업로드 완료 후 무결성 검증 |

### Response

```
HTTP/1.1 201 Created
Location: /files/b7bd99b9-a5f0-48ae-b6b5-682dbcd1a522
X-Session-Id: 357efd8a-0c0c-40cf-a2f6-6b506616bb11
Tus-Resumable: 1.0.0
```

| 응답 헤더 | 설명 |
|---|---|
| `Location` | 이후 PATCH / HEAD / DELETE에 사용할 TUS URI |
| `X-Session-Id` | 내부 세션 UUID (참고용) |

> `Location` 값을 저장해 두어야 합니다. `X-Session-Id`는 PATCH에 사용하지 않습니다.

### 오류

| HTTP | code | 설명 |
|---|---|---|
| 401 | — | JWT 없음 또는 만료 |
| 403 | 1006 | 라이선스 만료 |
| 507 | 1005 | 저장 쿼터 초과 — FILE 타입은 오래된 파일 자동 삭제 후에도 공간이 부족한 경우에만 반환 |

---

## 3. 파일 전송 (PATCH)

`Upload-Length`만큼 데이터를 전송합니다. 한 번에 전체를 보내거나 여러 청크로 나눌 수 있습니다.

### Request

```
PATCH /files/{tusId}
Authorization: Bearer {accessToken}
Tus-Resumable: 1.0.0
Content-Type: application/offset+octet-stream
Upload-Offset: 0
Content-Length: {이번 청크 크기}

(binary data)
```

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Authorization` | ✅ | `Bearer {accessToken}` |
| `Tus-Resumable` | ✅ | `1.0.0` 고정 |
| `Content-Type` | ✅ | `application/offset+octet-stream` 고정 |
| `Upload-Offset` | ✅ | 이번 청크의 시작 위치 (bytes). 첫 요청은 `0` |
| `Content-Length` | ✅ | 이번 청크 크기 (bytes) |

### Response

```
HTTP/1.1 204 No Content
Upload-Offset: 1048576
Tus-Resumable: 1.0.0
```

| 응답 헤더 | 설명 |
|---|---|
| `Upload-Offset` | 서버가 수신한 누적 bytes. 다음 PATCH의 `Upload-Offset`으로 사용 |

> `Upload-Offset` == `Upload-Length` 이면 업로드 완료입니다.

### 마지막 청크 처리 (자동)

마지막 청크 수신 시 서버가 자동으로 처리합니다.

1. `Upload-Sha256` 헤더를 전달했다면 SHA-256 무결성 검증
2. 파일을 영구 저장소로 이동
3. 세션 status → `COMPLETED`

### 오류

| HTTP | code | 설명 |
|---|---|---|
| 401 | — | JWT 없음 또는 만료 → 토큰 재발급 후 재시도 |
| 403 | 1004 | 다른 병원의 세션에 접근 시도 |
| 404 | 1003 | 세션 없음 (만료 또는 잘못된 tusId) |
| 409 | — | `Upload-Offset` 불일치 → HEAD로 현재 offset 확인 후 재시도 |
| 422 | 1007 | SHA-256 불일치 — 파일 손상, 재업로드 필요 |

---

## 4. Resume (네트워크 중단 후 재개)

네트워크가 끊긴 경우 HEAD로 서버의 현재 offset을 확인한 후 해당 위치부터 PATCH를 재개합니다.

### Request

```
HEAD /files/{tusId}
Authorization: Bearer {accessToken}
Tus-Resumable: 1.0.0
```

### Response

```
HTTP/1.1 204 No Content
Upload-Offset: 52428800
Upload-Length: 104857600
Tus-Resumable: 1.0.0
```

이후 `Upload-Offset: 52428800`부터 PATCH를 재개합니다.

---

## 5. 업로드 중단 (DELETE)

업로드를 취소하고 서버의 임시 파일을 삭제합니다.

### Request

```
DELETE /files/{tusId}
Authorization: Bearer {accessToken}
Tus-Resumable: 1.0.0
```

### Response

```
HTTP/1.1 204 No Content
```

---

## 파일명 변환 규칙

서버는 저장 경로 생성 시 파일명의 특수문자를 `_`로 치환합니다.

- 허용 문자: `A-Z a-z 0-9 . _ -`
- 나머지 문자 (`공백`, `()`, `[]`, 한글 등): `_`로 치환

**예시**

| 전달한 파일명 | 저장 파일명 |
|---|---|
| `backup 2026-05-12.zip` | `backup_2026-05-12.zip` |
| `병원백업(최종).zip` | `______(____).zip` |
| `backup-final_v2.zip` | `backup-final_v2.zip` (변환 없음) |

> 원본 파일명은 `upload_session.original_filename`에 그대로 보존됩니다.

---

## 세션 만료 및 보관 정책

### 세션 / 토큰 TTL

| 항목 | 값 |
|---|---|
| 업로드 세션 TTL | 생성 후 24시간 |
| JWT TTL | 발급 후 15분 |

> 세션이 24시간 내에 완료되지 않으면 `EXPIRED` 상태로 전환되고 임시 파일이 삭제됩니다.
> JWT가 만료되더라도 세션 자체는 유효합니다. 토큰을 재발급 받아 업로드를 이어가면 됩니다.

### 파일 보관 정책

파일은 **무기한 보관**되며, 별도의 만료 삭제 없이 아래 정책에 따라 자동 관리됩니다.

| 타입 | 정책 |
|---|---|
| `DB` | 병원당 최신 **3개**만 유지. 4번째 업로드 완료 시 가장 오래된 것 자동 삭제 |
| `FILE` | 병원별 쿼터(등록 시 지정한 `maxStorageBytes`) 기반 관리. 초과 시 오래된 파일부터 자동 삭제하여 공간 확보 후 저장 |

> DB 파일은 쿼터 용량과 무관하게 항상 최신 3개가 유지됩니다.
> FILE 타입도 모든 기존 파일을 삭제해도 공간이 부족하면 507을 반환합니다.

---

## 에러 응답 형식

```json
{
  "code": 1005,
  "message": "Storage quota exceeded"
}
```

| 필드 | 설명 |
|---|---|
| `code` | 서버 내부 에러 코드 |
| `message` | 에러 설명 |
| `errors` | 유효성 검사 오류 시 필드별 상세 (생략 가능) |

### 에러 코드 전체 목록

| code | HTTP | 설명 |
|---|---|---|
| 1003 | 404 | 업로드 세션 없음 |
| 1004 | 403 | 다른 병원 세션 접근 금지 |
| 1005 | 507 | 저장 쿼터 초과 |
| 1006 | 403 | 라이선스 만료 |
| 1007 | 422 | SHA-256 무결성 검증 실패 |
| 1008 | 400 | 요청 유효성 검사 실패 |
| 1009 | 400 | 잘못된 인자 |
| 1010 | 500 | 서버 내부 오류 |

---

## 구현 참고 — 청크 업로드 예시 (JavaScript / fetch)

```javascript
const BASE_URL = "https://backup.intocns.com:8282";
const CHUNK_SIZE = 10 * 1024 * 1024; // 10MB

// 1. JWT 발급
async function fetchToken(clientId, clientSecret) {
  const res = await fetch(`${BASE_URL}/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ clientId, clientSecret }),
  });
  if (!res.ok) throw new Error(`토큰 발급 실패: ${res.status}`);
  const { accessToken } = await res.json();
  return accessToken;
}

// 2. SHA-256 계산
async function sha256hex(buffer) {
  const hashBuffer = await crypto.subtle.digest("SHA-256", buffer);
  return Array.from(new Uint8Array(hashBuffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

// 3. 업로드 세션 생성
async function createSession(token, filename, fileSize, sha256) {
  const res = await fetch(`${BASE_URL}/files`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Tus-Resumable": "1.0.0",
      "Upload-Type": "DB",
      "Upload-Filename": filename,
      "Upload-Length": String(fileSize),
      ...(sha256 && { "Upload-Sha256": sha256 }),
    },
  });
  if (!res.ok) throw new Error(`세션 생성 실패: ${res.status}`);
  return res.headers.get("Location"); // e.g. /files/{tusId}
}

// 4. 청크 전송
async function uploadChunks(token, tusUri, fileBuffer) {
  let offset = 0;
  while (offset < fileBuffer.byteLength) {
    const chunk = fileBuffer.slice(offset, offset + CHUNK_SIZE);
    const res = await fetch(`${BASE_URL}${tusUri}`, {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${token}`,
        "Tus-Resumable": "1.0.0",
        "Content-Type": "application/offset+octet-stream",
        "Upload-Offset": String(offset),
        "Content-Length": String(chunk.byteLength),
      },
      body: chunk,
    });
    if (!res.ok) throw new Error(`청크 전송 실패: ${res.status}`);
    offset = Number(res.headers.get("Upload-Offset"));
    console.log(`업로드 진행: ${offset} / ${fileBuffer.byteLength} bytes`);
  }
}

// 5. Resume — 현재 offset 조회
async function getOffset(token, tusUri) {
  const res = await fetch(`${BASE_URL}${tusUri}`, {
    method: "HEAD",
    headers: {
      Authorization: `Bearer ${token}`,
      "Tus-Resumable": "1.0.0",
    },
  });
  if (!res.ok) throw new Error(`offset 조회 실패: ${res.status}`);
  return Number(res.headers.get("Upload-Offset"));
}

// 전체 업로드 흐름
async function upload(clientId, clientSecret, file) {
  const buffer = await file.arrayBuffer();
  const hash = await sha256hex(buffer);
  const token = await fetchToken(clientId, clientSecret);
  const tusUri = await createSession(token, file.name, buffer.byteLength, hash);
  await uploadChunks(token, tusUri, buffer);
  console.log("업로드 완료");
}
```

---

## 구현 참고 — tus-js-client 사용 시

```javascript
import * as tus from "tus-js-client";
import { createHash } from "crypto";
import { readFileSync, statSync } from "fs";

const BASE_URL = "https://backup.intocns.com:8282";

async function fetchToken(clientId, clientSecret) {
  const res = await fetch(`${BASE_URL}/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ clientId, clientSecret }),
  });
  const { accessToken } = await res.json();
  return accessToken;
}

async function uploadWithTusClient(clientId, clientSecret, filePath) {
  const token = await fetchToken(clientId, clientSecret);
  const fileData = readFileSync(filePath);
  const fileSize = statSync(filePath).size;
  const sha256 = createHash("sha256").update(fileData).digest("hex");
  const filename = filePath.split("/").pop();

  return new Promise((resolve, reject) => {
    const upload = new tus.Upload(fileData, {
      endpoint: `${BASE_URL}/files`,
      retryDelays: [0, 3000, 5000, 10000],
      chunkSize: 10 * 1024 * 1024,
      headers: {
        Authorization: `Bearer ${token}`,
        "Upload-Type": "DB",
        "Upload-Filename": filename,
        "Upload-Sha256": sha256,
      },
      uploadSize: fileSize,
      onError: reject,
      onProgress: (uploaded, total) =>
        console.log(`업로드 진행: ${uploaded} / ${total} bytes`),
      onSuccess: resolve,
    });
    upload.start();
  });
}
```
