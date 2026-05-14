CREATE TABLE auth_audit_log
(
    id         CHAR(36)                    NOT NULL,
    client_id  VARCHAR(64)                 NOT NULL,
    cocode     BIGINT,
    ip_address VARCHAR(45)                 NOT NULL,
    result     ENUM ('SUCCESS', 'FAILED')  NOT NULL,
    created_at DATETIME(6)                 NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_auth_audit_log_client_id ON auth_audit_log (client_id);
CREATE INDEX idx_auth_audit_log_cocode ON auth_audit_log (cocode);
CREATE INDEX idx_auth_audit_log_created ON auth_audit_log (created_at DESC);
