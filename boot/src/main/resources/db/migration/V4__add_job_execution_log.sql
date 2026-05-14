CREATE TABLE job_execution_log (
    id          CHAR(36)                 NOT NULL,
    job_name    VARCHAR(100)             NOT NULL,
    started_at  DATETIME(6)              NOT NULL,
    finished_at DATETIME(6)              NOT NULL,
    status      ENUM('SUCCESS','FAILED') NOT NULL,
    summary     JSON,
    error_msg   VARCHAR(1000),
    PRIMARY KEY (id),
    INDEX idx_job_execution_log_job_name_started (job_name, started_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
