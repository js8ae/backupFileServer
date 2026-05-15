package com.intocns.backup.api.admin.dto;

import com.intocns.backup.application.DetectMissingBackupsUseCase.MissingInfo;

import java.time.Instant;

public record MissingBackupResponse(
        long cocode,
        String name,
        TypeStatus db,
        TypeStatus file
) {
    public record TypeStatus(boolean missing, Instant lastBackupAt, Long hoursSinceLastBackup) {}

    public static MissingBackupResponse from(MissingInfo info) {
        return new MissingBackupResponse(
                info.hospital().id().cocode(),
                info.hospital().name(),
                new TypeStatus(info.dbMissing(), info.dbLastBackupAt(), info.dbHoursSinceLastBackup()),
                new TypeStatus(info.fileMissing(), info.fileLastBackupAt(), info.fileHoursSinceLastBackup())
        );
    }
}
