-- MariaDB / UTF-8mb4 / DATETIME(6) UTC 기준 (애플리케이션 책임)
-- cocode = 글로벌 병원 식별 키 (Long), 모든 테이블에서 동일한 컬럼명 사용

CREATE TABLE hospital
(
    cocode            BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    license_start_at  DATETIME(6)  NOT NULL,
    license_end_at    DATETIME(6)  NOT NULL,
    max_storage_bytes BIGINT       NOT NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (cocode)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE upload_session
(
    id                CHAR(36)                                                            NOT NULL,
    cocode            BIGINT                                                              NOT NULL,
    type              ENUM ('DB', 'FILE')                                                 NOT NULL,
    original_filename VARCHAR(512)                                                        NOT NULL,
    total_size        BIGINT                                                              NOT NULL,
    current_offset    BIGINT                                                              NOT NULL DEFAULT 0,
    expected_sha256   VARCHAR(64),
    tus_upload_uri    VARCHAR(512),
    status            ENUM ('INITIATED', 'UPLOADING', 'COMPLETED', 'ABORTED', 'EXPIRED') NOT NULL,
    expires_at        DATETIME(6)                                                         NOT NULL,
    created_at        DATETIME(6)                                                         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_upload_session_hospital FOREIGN KEY (cocode) REFERENCES hospital (cocode)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_upload_session_cocode ON upload_session (cocode);
CREATE INDEX idx_upload_session_expires_at ON upload_session (expires_at);
CREATE INDEX idx_upload_session_tus_upload_uri ON upload_session (tus_upload_uri(191));


CREATE TABLE backup_artifact
(
    id           CHAR(36)            NOT NULL,
    cocode       BIGINT              NOT NULL,
    type         ENUM ('DB', 'FILE') NOT NULL,
    storage_path VARCHAR(1024)       NOT NULL,
    size_bytes   BIGINT              NOT NULL,
    sha256       VARCHAR(64)         NOT NULL,
    created_at   DATETIME(6)         NOT NULL,
    expires_at   DATETIME(6)         NOT NULL,
    purged_at    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_backup_artifact_hospital FOREIGN KEY (cocode) REFERENCES hospital (cocode)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_backup_artifact_cocode_created ON backup_artifact (cocode, created_at DESC);
CREATE INDEX idx_backup_artifact_expires_at ON backup_artifact (expires_at);


CREATE TABLE hospital_quota
(
    cocode             BIGINT      NOT NULL,
    used_bytes         BIGINT      NOT NULL DEFAULT 0,
    limit_bytes        BIGINT      NOT NULL,
    last_calculated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (cocode),
    CONSTRAINT fk_hospital_quota_hospital FOREIGN KEY (cocode) REFERENCES hospital (cocode)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE hospital_credential
(
    cocode             BIGINT       NOT NULL,
    client_id          VARCHAR(64)  NOT NULL,
    client_secret_hash VARCHAR(255) NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    revoked_at         DATETIME(6),
    PRIMARY KEY (cocode, client_id),
    CONSTRAINT fk_hospital_credential_hospital FOREIGN KEY (cocode) REFERENCES hospital (cocode)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_hospital_credential_client_id ON hospital_credential (client_id);


CREATE TABLE backup_audit_log
(
    id          CHAR(36)    NOT NULL,
    session_id  CHAR(36),
    artifact_id CHAR(36),
    cocode      BIGINT      NOT NULL,
    event       VARCHAR(64) NOT NULL,
    detail      JSON,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_audit_log_cocode_created ON backup_audit_log (cocode, created_at DESC);
CREATE INDEX idx_audit_log_session_id ON backup_audit_log (session_id);
