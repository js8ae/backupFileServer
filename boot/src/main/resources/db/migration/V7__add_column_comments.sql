-- 전 테이블/전 컬럼에 코멘트 부여 (운영 가독성·신규 인입자 온보딩용)
-- V1~V6는 운영에 이미 적용되어 checksum 보호를 위해 수정하지 않고, 별도 ALTER로 코멘트만 추가한다.
-- MariaDB는 MODIFY COLUMN 시 컬럼 정의 전체를 재기술해야 하므로 V1~V6의 최종 정의(V2 nullable, V5 추가 컬럼 반영)와 동일하게 맞춘다.
-- 모든 DATETIME(6)는 애플리케이션이 UTC로 기록(읽기는 KST 변환은 로깅 레이어 책임).

-- ──────────────────────────────────────────────────────────────
-- hospital : 병원 마스터 (라이선스·저장 한도의 기준)
-- ──────────────────────────────────────────────────────────────
ALTER TABLE hospital
    MODIFY COLUMN cocode            BIGINT       NOT NULL                COMMENT '병원 식별 코드(글로벌 PK). 전 테이블 공통 조인 키',
    MODIFY COLUMN name              VARCHAR(255) NOT NULL                COMMENT '병원명',
    MODIFY COLUMN license_start_at  DATETIME(6)  NOT NULL                COMMENT '라이선스 유효 시작 일시(UTC)',
    MODIFY COLUMN license_end_at    DATETIME(6)  NOT NULL                COMMENT '라이선스 만료 일시(UTC). 만료 시 업로드 세션 생성 403',
    MODIFY COLUMN max_storage_bytes BIGINT       NOT NULL                COMMENT '병원 저장 한도(bytes). hospital_quota.limit_bytes 산정의 원천값',
    MODIFY COLUMN is_active         BOOLEAN      NOT NULL DEFAULT TRUE   COMMENT '병원 활성 여부. false면 비활성 처리',
    MODIFY COLUMN created_at        DATETIME(6)  NOT NULL                COMMENT '레코드 생성 일시(UTC)',
    MODIFY COLUMN updated_at        DATETIME(6)  NOT NULL                COMMENT '레코드 최종 수정 일시(UTC)';

ALTER TABLE hospital COMMENT = '병원 마스터. 라이선스 기간과 저장 한도의 기준 테이블';


-- ──────────────────────────────────────────────────────────────
-- upload_session : TUS 청크 업로드 세션 (진행/재개 상태)
-- ──────────────────────────────────────────────────────────────
ALTER TABLE upload_session
    MODIFY COLUMN id                CHAR(36)            NOT NULL                COMMENT '세션 ID(UUID). 클라이언트에 발급되는 업로드 식별자',
    MODIFY COLUMN cocode            BIGINT              NOT NULL                COMMENT '세션 소유 병원 코드(FK hospital). JWT sub와 일치 검증 대상',
    MODIFY COLUMN type              ENUM('DB','FILE')   NOT NULL                COMMENT '백업 유형. DB=덤프(병원당 최대 3개), FILE=영상/이미지(쿼터 기반 eviction)',
    MODIFY COLUMN original_filename VARCHAR(512)        NOT NULL                COMMENT '클라이언트가 전달한 원본 파일명(sanitize 전 값)',
    MODIFY COLUMN total_size        BIGINT              NOT NULL                COMMENT '업로드 대상 전체 크기(bytes). PATCH가 이 값에 도달하면 finalize',
    MODIFY COLUMN current_offset    BIGINT              NOT NULL DEFAULT 0      COMMENT '현재까지 수신된 누적 바이트 오프셋. Resume 시 기준점',
    MODIFY COLUMN expected_sha256   VARCHAR(64)         NULL                    COMMENT '클라이언트가 선언한 기대 SHA-256(hex). finalize 무결성 검증에 사용',
    MODIFY COLUMN tus_upload_uri    VARCHAR(512)        NULL                    COMMENT 'tus-java-server가 발급한 업로드 URI(incoming/uploads/{uuid}). 세션과 TUS 리소스 연결',
    MODIFY COLUMN status            ENUM('INITIATED','UPLOADING','COMPLETED','ABORTED','EXPIRED') NOT NULL COMMENT '세션 상태. INITIATED→UPLOADING→COMPLETED, 또는 ABORTED/EXPIRED 종료',
    MODIFY COLUMN expires_at        DATETIME(6)         NOT NULL                COMMENT '세션 만료 일시(UTC). 초과 시 ExpiredSessionCleanupJob이 ABORTED 처리',
    MODIFY COLUMN created_at        DATETIME(6)         NOT NULL                COMMENT '세션 생성 일시(UTC)';

ALTER TABLE upload_session COMMENT = 'TUS 청크 업로드 세션. Stop/Resume 진행 상태 및 무결성 기대값 보관';


-- ──────────────────────────────────────────────────────────────
-- backup_artifact : 영구 보관된 백업 산출물 (artifacts 영역 실물 메타)
-- ──────────────────────────────────────────────────────────────
ALTER TABLE backup_artifact
    MODIFY COLUMN id                CHAR(36)            NOT NULL                COMMENT '아티팩트 ID(UUID)',
    MODIFY COLUMN cocode            BIGINT              NOT NULL                COMMENT '소유 병원 코드(FK hospital)',
    MODIFY COLUMN type              ENUM('DB','FILE')   NOT NULL                COMMENT '백업 유형. DB=병원당 최대 3개 유지, FILE=쿼터 초과 시 oldest부터 eviction',
    MODIFY COLUMN storage_path      VARCHAR(1024)       NOT NULL                COMMENT '영구 저장 경로(artifacts-root 기준 상대/절대 경로)',
    MODIFY COLUMN original_filename VARCHAR(500)        NULL                    COMMENT '원본 파일명(V5 추가). 다운로드/표시용. 과거 데이터는 NULL일 수 있음',
    MODIFY COLUMN size_bytes        BIGINT              NOT NULL                COMMENT '아티팩트 크기(bytes). hospital_quota.used_bytes 증감의 단위',
    MODIFY COLUMN sha256            VARCHAR(64)         NOT NULL                COMMENT '저장 시점 SHA-256(hex). IntegrityVerificationJob 비트부패 재검증 기준',
    MODIFY COLUMN created_at        DATETIME(6)         NOT NULL                COMMENT '아티팩트 생성(promote) 일시(UTC)',
    MODIFY COLUMN expires_at        DATETIME(6)         NULL                    COMMENT '만료 일시(UTC). NULL=무기한 보관(V2). 값 존재 시 RetentionPolicyJob 대상',
    MODIFY COLUMN purged_at         DATETIME(6)         NULL                    COMMENT 'trash 이동(소프트 삭제) 처리 일시(UTC). NULL=활성. markPurged 멱등 처리 기준';

ALTER TABLE backup_artifact COMMENT = '영구 보관 백업 산출물 메타. 무기한 보관(expires_at NULL) 기본, 쿼터/개수 정책에 따라 eviction';


-- ──────────────────────────────────────────────────────────────
-- hospital_quota : 병원별 저장 사용량/한도
-- ──────────────────────────────────────────────────────────────
ALTER TABLE hospital_quota
    MODIFY COLUMN cocode             BIGINT      NOT NULL              COMMENT '병원 코드(PK, FK hospital)',
    MODIFY COLUMN used_bytes         BIGINT      NOT NULL DEFAULT 0    COMMENT '현재 사용량(bytes). DB+FILE 아티팩트 합계. DB 예약분 포함이라 limit 초과 가능(의도적)',
    MODIFY COLUMN limit_bytes        BIGINT      NOT NULL              COMMENT '저장 한도(bytes). FILE 가용량 = limit_bytes - DB 예약분(dbReserved)',
    MODIFY COLUMN last_calculated_at DATETIME(6) NOT NULL              COMMENT '사용량 최종 재계산 일시(UTC)';

ALTER TABLE hospital_quota COMMENT = '병원별 저장 사용량/한도. used_bytes는 DB 예약분 포함으로 limit 초과 가능';


-- ──────────────────────────────────────────────────────────────
-- hospital_credential : 병원별 클라이언트 인증 정보 (1,200+)
-- ──────────────────────────────────────────────────────────────
ALTER TABLE hospital_credential
    MODIFY COLUMN cocode             BIGINT       NOT NULL             COMMENT '병원 코드(PK 일부, FK hospital)',
    MODIFY COLUMN client_id          VARCHAR(64)  NOT NULL             COMMENT '클라이언트 ID(PK 일부). 인증 요청 식별자',
    MODIFY COLUMN client_secret_hash VARCHAR(255) NOT NULL             COMMENT 'client_secret의 bcrypt 해시. 평문은 발급 시 1회만 노출, 서버는 해시만 보관',
    MODIFY COLUMN created_at         DATETIME(6)  NOT NULL             COMMENT '자격증명 발급 일시(UTC)',
    MODIFY COLUMN revoked_at         DATETIME(6)  NULL                 COMMENT '폐기 일시(UTC). NULL=유효, 값 존재=소프트 삭제(폐기됨)';

ALTER TABLE hospital_credential COMMENT = '병원별 client_id/secret(bcrypt). 발급/폐기/회전은 Admin API로 관리';


-- ──────────────────────────────────────────────────────────────
-- backup_audit_log : 업로드 라이프사이클 감사 로그
-- ──────────────────────────────────────────────────────────────
ALTER TABLE backup_audit_log
    MODIFY COLUMN id          CHAR(36)    NOT NULL                COMMENT '감사 로그 ID(UUID)',
    MODIFY COLUMN session_id  CHAR(36)    NULL                    COMMENT '관련 업로드 세션 ID. 세션 무관 이벤트(HOSPITAL_RESET 등)는 NULL',
    MODIFY COLUMN artifact_id CHAR(36)    NULL                    COMMENT '관련 아티팩트 ID. 아티팩트 무관 이벤트는 NULL',
    MODIFY COLUMN cocode      BIGINT      NOT NULL                COMMENT '이벤트 주체 병원 코드',
    MODIFY COLUMN event       VARCHAR(64) NOT NULL                COMMENT '이벤트 종류. UPLOAD_INITIATED/COMPLETED/ABORTED/EXPIRED, ARTIFACT_EVICTED, HOSPITAL_RESET',
    MODIFY COLUMN detail      JSON        NULL                    COMMENT '이벤트 부가 정보(key-value JSON). 파일명/사유/크기 등 가변 컨텍스트',
    MODIFY COLUMN created_at  DATETIME(6) NOT NULL                COMMENT '이벤트 발생 일시(UTC)';

ALTER TABLE backup_audit_log COMMENT = '업로드 라이프사이클·데이터 변경 감사 로그';


-- ──────────────────────────────────────────────────────────────
-- auth_audit_log : 인증 성공/실패 감사 로그 (V3)
-- ──────────────────────────────────────────────────────────────
ALTER TABLE auth_audit_log
    MODIFY COLUMN id         CHAR(36)                   NOT NULL     COMMENT '인증 감사 로그 ID(UUID)',
    MODIFY COLUMN client_id  VARCHAR(64)                NOT NULL     COMMENT '인증 시도 client_id(존재하지 않는 ID여도 시도값 기록)',
    MODIFY COLUMN cocode     BIGINT                     NULL         COMMENT '매칭된 병원 코드. 미상(인증 실패 등)이면 NULL',
    MODIFY COLUMN ip_address VARCHAR(45)                NOT NULL     COMMENT '요청 출발 IP(IPv4/IPv6). nginx forwarded 헤더 기반',
    MODIFY COLUMN result     ENUM('SUCCESS','FAILED')   NOT NULL     COMMENT '인증 결과',
    MODIFY COLUMN created_at DATETIME(6)                NOT NULL     COMMENT '인증 시도 일시(UTC)';

ALTER TABLE auth_audit_log COMMENT = '/auth/token 인증 시도 감사 로그(성공/실패)';


-- ──────────────────────────────────────────────────────────────
-- job_execution_log : 스케줄러 실행 결과 로그 (V4)
-- ──────────────────────────────────────────────────────────────
ALTER TABLE job_execution_log
    MODIFY COLUMN id          CHAR(36)                 NOT NULL      COMMENT '실행 로그 ID(UUID)',
    MODIFY COLUMN job_name    VARCHAR(100)             NOT NULL      COMMENT '잡 이름. QuotaRebalanceJob/RetentionPolicyJob/IntegrityVerificationJob/TrashCleanupJob/ExpiredSessionCleanupJob',
    MODIFY COLUMN started_at  DATETIME(6)              NOT NULL      COMMENT '실행 시작 일시(UTC)',
    MODIFY COLUMN finished_at DATETIME(6)              NOT NULL      COMMENT '실행 종료 일시(UTC). 소요 시간 = finished_at - started_at',
    MODIFY COLUMN status      ENUM('SUCCESS','FAILED') NOT NULL      COMMENT '실행 결과. 트랜잭션 롤백 시에도 이 로그는 커밋됨',
    MODIFY COLUMN summary     JSON                     NULL          COMMENT '처리 요약(JSON). 처리 건수 등 잡별 지표',
    MODIFY COLUMN error_msg   VARCHAR(1000)            NULL          COMMENT '실패 시 에러 메시지. 성공이면 NULL';

ALTER TABLE job_execution_log COMMENT = '배치 스케줄러 실행 결과(성공/실패/처리량/소요시간)';


-- ──────────────────────────────────────────────────────────────
-- client_upload_error_log : 클라이언트 업로드 에러 리포팅 (V6)
-- ──────────────────────────────────────────────────────────────
ALTER TABLE client_upload_error_log
    MODIFY COLUMN id            CHAR(36)      NOT NULL              COMMENT '에러 리포트 ID(UUID)',
    MODIFY COLUMN session_id    CHAR(36)      NULL                  COMMENT '관련 업로드 세션 ID. 미상이면 NULL',
    MODIFY COLUMN cocode        BIGINT        NULL                  COMMENT '병원 코드. 결정 우선순위: JWT 유효→sessionId 조회→NULL',
    MODIFY COLUMN error_type    VARCHAR(64)   NOT NULL              COMMENT '에러 유형(자유 문자열, 필수). 클라이언트가 분류한 코드',
    MODIFY COLUMN error_message VARCHAR(1000) NULL                  COMMENT '에러 상세 메시지(선택)',
    MODIFY COLUMN byte_offset   BIGINT        NULL                  COMMENT '에러 발생 시점의 바이트 오프셋(선택)',
    MODIFY COLUMN client_info   JSON          NULL                  COMMENT '클라이언트 환경 정보(OS/버전/네트워크 등, 선택 JSON)',
    MODIFY COLUMN occurred_at   DATETIME(6)   NOT NULL              COMMENT '클라이언트 측 에러 발생 일시(UTC)',
    MODIFY COLUMN reported_at   DATETIME(6)   NOT NULL              COMMENT '서버 수신/기록 일시(UTC)';

ALTER TABLE client_upload_error_log COMMENT = '클라이언트가 보고한 업로드 실패 이벤트. JWT·sessionId 모두 선택';
