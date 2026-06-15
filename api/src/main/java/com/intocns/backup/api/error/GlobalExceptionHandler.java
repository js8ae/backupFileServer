package com.intocns.backup.api.error;

import com.intocns.backup.domain.exception.ArtifactNotFoundException;
import com.intocns.backup.domain.exception.ArtifactNotPurgedException;
import com.intocns.backup.domain.exception.CredentialNotFoundException;
import com.intocns.backup.domain.exception.HospitalAlreadyExistsException;
import com.intocns.backup.domain.exception.HospitalNotFoundException;
import com.intocns.backup.domain.exception.IntegrityCheckFailedException;
import com.intocns.backup.domain.exception.LicenseExpiredException;
import com.intocns.backup.domain.exception.QuotaExceededException;
import com.intocns.backup.domain.exception.SessionNotFoundException;
import com.intocns.backup.domain.exception.UnauthorizedSessionAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ArtifactNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleArtifactNotFound(ArtifactNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.ARTIFACT_NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(ArtifactNotPurgedException.class)
    public ResponseEntity<ErrorResponse> handleArtifactNotPurged(ArtifactNotPurgedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.ARTIFACT_NOT_PURGED, e.getMessage()));
    }

    @ExceptionHandler(HospitalAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleHospitalAlreadyExists(HospitalAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.HOSPITAL_ALREADY_EXISTS, e.getMessage()));
    }

    @ExceptionHandler(HospitalNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHospitalNotFound(HospitalNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.HOSPITAL_NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(CredentialNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCredentialNotFound(CredentialNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.CREDENTIAL_NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.SESSION_NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedSessionAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedSessionAccessException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ErrorCode.SESSION_FORBIDDEN, e.getMessage()));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(QuotaExceededException e) {
        return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                .body(ErrorResponse.of(ErrorCode.QUOTA_EXCEEDED, e.getMessage()));
    }

    @ExceptionHandler(LicenseExpiredException.class)
    public ResponseEntity<ErrorResponse> handleLicenseExpired(LicenseExpiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ErrorCode.LICENSE_EXPIRED, e.getMessage()));
    }

    @ExceptionHandler(IntegrityCheckFailedException.class)
    public ResponseEntity<ErrorResponse> handleIntegrityCheckFailed(IntegrityCheckFailedException e) {
        log.error("Integrity check failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ErrorCode.INTEGRITY_CHECK_FAILED, e.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(ErrorCode.INVALID_ARGUMENT, "Unsupported Content-Type. Use application/json"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_ARGUMENT, "Malformed or missing request body"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "Request validation failed", fieldErrors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_ARGUMENT, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));
    }
}
