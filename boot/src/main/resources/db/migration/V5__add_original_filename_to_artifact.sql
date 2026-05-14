ALTER TABLE backup_artifact
    ADD COLUMN original_filename VARCHAR(500) NULL AFTER storage_path;
