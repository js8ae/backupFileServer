package com.intocns.backup.api.admin.dto;

import java.util.List;

public record BulkRegisterResponse(
        List<HospitalResponse> succeeded,
        List<FailedItem> failed
) {
    public record FailedItem(long cocode, String reason) {}
}
