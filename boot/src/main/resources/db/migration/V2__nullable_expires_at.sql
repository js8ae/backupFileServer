-- backup_artifact.expires_at: 무기한 보관 정책 지원을 위해 NULL 허용
ALTER TABLE backup_artifact MODIFY COLUMN expires_at DATETIME(6) NULL;
