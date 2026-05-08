package com.intocns.backup.api.error;

public enum ErrorCode {

    // 4xx — 클라이언트 오류
    HOSPITAL_ALREADY_EXISTS(1000, "Hospital already exists"),
    HOSPITAL_NOT_FOUND(1001, "Hospital not found"),
    CREDENTIAL_NOT_FOUND(1002, "Credential not found"),
    SESSION_NOT_FOUND(1003, "Upload session not found"),
    SESSION_FORBIDDEN(1004, "Access to this session is not allowed"),
    QUOTA_EXCEEDED(1005, "Storage quota exceeded"),
    LICENSE_EXPIRED(1006, "License has expired"),
    INTEGRITY_CHECK_FAILED(1007, "File integrity check failed"),
    VALIDATION_FAILED(1008, "Request validation failed"),
    INVALID_ARGUMENT(1009, "Invalid argument"),

    // 5xx — 서버 오류
    INTERNAL_ERROR(1010, "An unexpected error occurred");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
