CREATE TABLE client_upload_error_log (
    id            CHAR(36)      NOT NULL,
    session_id    CHAR(36)      NOT NULL,
    cocode        BIGINT        NOT NULL,
    error_type    VARCHAR(64)   NOT NULL,
    error_message VARCHAR(1000) NULL,
    byte_offset   BIGINT        NULL,
    client_info   JSON          NULL,
    occurred_at   DATETIME(6)   NOT NULL,
    reported_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_client_error_session (session_id),
    INDEX idx_client_error_cocode  (cocode, reported_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
