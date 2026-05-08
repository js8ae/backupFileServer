package com.intocns.backup;

import com.intocns.backup.api.auth.dto.TokenResponse;
import com.intocns.backup.domain.model.*;
import com.intocns.backup.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TusUploadE2EIT extends AbstractIT {

    @LocalServerPort int port;

    @Autowired HospitalRepository hospitalRepository;
    @Autowired HospitalCredentialRepository credentialRepository;
    @Autowired QuotaRepository quotaRepository;
    @Autowired UploadSessionRepository sessionRepository;
    @Autowired ArtifactRepository artifactRepository;
    @Autowired PasswordHasher passwordHasher;

    RestTemplate http;
    String base;

    @BeforeEach
    void setUpClient() {
        http = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
        http.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(HttpStatusCode statusCode) {
                return false;
            }
        });
        base = "http://localhost:" + port;
    }

    // ── 시나리오 1: 정상 청크 업로드 완료 ─────────────────────

    @Test
    void 청크_업로드_완료_플로우() throws Exception {
        HospitalId hospitalId = new HospitalId(20001L);
        String jwt = givenHospital(hospitalId, "정상병원", validLicense());

        byte[] chunk1 = generateBytes(512, 0);
        byte[] chunk2 = generateBytes(512, 1);
        byte[] chunk3 = generateBytes(512, 2);
        byte[] file = concat(chunk1, chunk2, chunk3);
        String sha256 = sha256hex(file);

        // TUS 세션 생성 (POST)
        ResponseEntity<Void> created = tusCreate(jwt, "DB", "backup.zip", sha256, file.length);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        URI locationUri = created.getHeaders().getLocation();
        assertNotNull(locationUri, "Location 헤더 누락");
        String tusUri = locationUri.getPath();
        String sessionId = created.getHeaders().getFirst("X-Session-Id");
        assertNotNull(sessionId, "X-Session-Id 헤더 누락");

        // 청크 3개 순차 전송 (PATCH)
        ResponseEntity<Void> p1 = tusPatch(jwt, tusUri, 0, chunk1);
        ResponseEntity<Void> p2 = tusPatch(jwt, tusUri, chunk1.length, chunk2);
        ResponseEntity<Void> p3 = tusPatch(jwt, tusUri, chunk1.length + chunk2.length, chunk3);

        assertEquals(HttpStatus.NO_CONTENT, p1.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, p2.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, p3.getStatusCode());

        // 세션 COMPLETED, artifact 생성 확인
        UploadSession session = sessionRepository.findById(UUID.fromString(sessionId)).orElseThrow();
        assertEquals(UploadStatus.COMPLETED, session.status());
        assertEquals(sha256, artifactRepository.findByHospitalId(hospitalId).get(0).sha256());
    }

    // ── 시나리오 2: 중단 후 Resume ───────────────────────────

    @Test
    void 중단_후_resume_플로우() throws Exception {
        HospitalId hospitalId = new HospitalId(20002L);
        String jwt = givenHospital(hospitalId, "재개병원", validLicense());

        byte[] chunk1 = generateBytes(512, 10);
        byte[] chunk2 = generateBytes(512, 11);
        byte[] chunk3 = generateBytes(512, 12);
        byte[] file = concat(chunk1, chunk2, chunk3);

        ResponseEntity<Void> created = tusCreate(jwt, "FILE", "scan.zip", null, file.length);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        String tusUri = created.getHeaders().getLocation().getPath();
        String sessionId = created.getHeaders().getFirst("X-Session-Id");

        // 첫 청크만 전송 후 중단 (네트워크 끊김 시뮬레이션)
        tusPatch(jwt, tusUri, 0, chunk1);

        // HEAD로 현재 offset 조회
        ResponseEntity<Void> headResp = http.exchange(
            base + tusUri, HttpMethod.HEAD,
            new HttpEntity<>(tusHeaders(jwt)), Void.class);

        // TUS spec: HEAD는 200 또는 204 모두 허용
        assertTrue(headResp.getStatusCode().is2xxSuccessful(), "HEAD 상태: " + headResp.getStatusCode());
        long resumeOffset = Long.parseLong(headResp.getHeaders().getFirst("Upload-Offset"));
        assertEquals(chunk1.length, resumeOffset);

        // 중단된 지점부터 재개
        tusPatch(jwt, tusUri, (int) resumeOffset, chunk2);
        tusPatch(jwt, tusUri, (int) resumeOffset + chunk2.length, chunk3);

        UploadSession session = sessionRepository.findById(UUID.fromString(sessionId)).orElseThrow();
        assertEquals(UploadStatus.COMPLETED, session.status());
    }

    // ── 시나리오 3: SHA-256 불일치 → 422 ────────────────────

    @Test
    void SHA256_불일치_422() throws Exception {
        String jwt = givenHospital(new HospitalId(20003L), "무결성실패병원", validLicense());

        byte[] data = generateBytes(512, 20);
        String wrongSha = sha256hex("corrupted-payload".getBytes());

        ResponseEntity<Void> created = tusCreate(jwt, "DB", "corrupt.zip", wrongSha, data.length);
        String tusUri = created.getHeaders().getLocation().getPath();
        String sessionId = created.getHeaders().getFirst("X-Session-Id");

        // 단일 PATCH로 업로드 완료 → finalize → SHA-256 불일치 → 422 + 에러 body
        ResponseEntity<String> resp = http.exchange(
            base + tusUri, HttpMethod.PATCH,
            new HttpEntity<>(data, patchHeaders(jwt, 0)), String.class);

        assertEquals(422, resp.getStatusCode().value());
        assertNotNull(resp.getBody(), "에러 body 유실 — response.reset() 미작동");
        assertTrue(resp.getBody().contains("INTEGRITY_CHECK_FAILED"),
            "응답 본문: " + resp.getBody());

        // 세션이 COMPLETED가 되어선 안 됨 (finalize 트랜잭션 롤백)
        assertNotNull(sessionId);
        UploadSession session = sessionRepository.findById(UUID.fromString(sessionId)).orElseThrow();
        assertNotEquals(UploadStatus.COMPLETED, session.status());
        assertTrue(artifactRepository.findByHospitalId(new HospitalId(20003L)).isEmpty());
    }

    // ── 시나리오 4: 쿼터 초과 → 507 ────────────────────────

    @Test
    void 쿼터_초과_507() throws Exception {
        HospitalId id = new HospitalId(20004L);
        String jwt = givenHospital(id, "쿼터초과병원", validLicense());
        quotaRepository.initializeQuota(id, 100L); // 100바이트 상한

        HttpHeaders headers = tusCreateHeaders(jwt, "DB", "big.zip", null, 1024L);
        ResponseEntity<String> resp = http.exchange(
            base + "/files", HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.INSUFFICIENT_STORAGE, resp.getStatusCode());
        assertTrue(resp.getBody().contains("QUOTA_EXCEEDED"),
            "응답 본문: " + resp.getBody());
    }

    // ── 시나리오 5: 라이선스 만료 → 403 ─────────────────────

    @Test
    void 라이선스_만료_403() throws Exception {
        String jwt = givenHospital(new HospitalId(20005L), "라이선스만료병원", expiredLicense());

        HttpHeaders headers = tusCreateHeaders(jwt, "DB", "test.zip", null, 512L);
        ResponseEntity<String> resp = http.exchange(
            base + "/files", HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertTrue(resp.getBody().contains("LICENSE_EXPIRED"),
            "응답 본문: " + resp.getBody());
    }

    // ── 헬퍼 ──────────────────────────────────────────────

    private String givenHospital(HospitalId id, String name, Instant[] license) {
        Instant now = Instant.now();
        if (hospitalRepository.findById(id).isEmpty()) {
            hospitalRepository.save(new Hospital(
                id, name, license[0], license[1], 10_737_418_240L, true, now, now));
        }
        String clientId = "hosp_" + UUID.randomUUID().toString().replace("-", "");
        String rawSecret = "secret-" + id.cocode();
        credentialRepository.save(id, clientId, passwordHasher.hash(rawSecret), now);
        return fetchJwt(clientId, rawSecret);
    }

    private String fetchJwt(String clientId, String secret) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"" + secret + "\"}";
        ResponseEntity<TokenResponse> r = http.exchange(
            base + "/auth/token", HttpMethod.POST, new HttpEntity<>(body, h), TokenResponse.class);
        assertEquals(HttpStatus.OK, r.getStatusCode(), "인증 실패");
        return "Bearer " + r.getBody().accessToken();
    }

    private ResponseEntity<Void> tusCreate(String jwt, String type, String filename, String sha256, long length) {
        return http.exchange(
            base + "/files", HttpMethod.POST,
            new HttpEntity<>(tusCreateHeaders(jwt, type, filename, sha256, length)),
            Void.class);
    }

    private HttpHeaders tusCreateHeaders(String jwt, String type, String filename, String sha256, long length) {
        HttpHeaders h = tusHeaders(jwt);
        h.set("Upload-Type", type);
        h.set("Upload-Filename", filename);
        h.set("Upload-Length", String.valueOf(length));
        h.setContentLength(0);
        if (sha256 != null) h.set("Upload-Sha256", sha256);
        return h;
    }

    private ResponseEntity<Void> tusPatch(String jwt, String tusUri, int offset, byte[] chunk) {
        return http.exchange(
            base + tusUri, HttpMethod.PATCH,
            new HttpEntity<>(chunk, patchHeaders(jwt, offset)), Void.class);
    }

    private HttpHeaders tusHeaders(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", jwt);
        h.set("Tus-Resumable", "1.0.0");
        return h;
    }

    private HttpHeaders patchHeaders(String jwt, int offset) {
        HttpHeaders h = tusHeaders(jwt);
        h.setContentType(MediaType.parseMediaType("application/offset+octet-stream"));
        h.set("Upload-Offset", String.valueOf(offset));
        return h;
    }

    private static Instant[] validLicense() {
        Instant now = Instant.now();
        return new Instant[]{now.minus(1, ChronoUnit.DAYS), now.plus(365, ChronoUnit.DAYS)};
    }

    private static Instant[] expiredLicense() {
        Instant now = Instant.now();
        return new Instant[]{now.minus(365, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)};
    }

    private static byte[] generateBytes(int size, int seed) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) b[i] = (byte) ((seed + i) & 0x7F);
        return b;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static String sha256hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
