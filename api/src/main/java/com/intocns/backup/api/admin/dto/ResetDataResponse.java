package com.intocns.backup.api.admin.dto;

import com.intocns.backup.application.ResetHospitalDataUseCase;

public record ResetDataResponse(int artifactsMoved, int sessionsAborted) {
    public static ResetDataResponse from(ResetHospitalDataUseCase.Result result) {
        return new ResetDataResponse(result.artifactsMoved(), result.sessionsAborted());
    }
}
