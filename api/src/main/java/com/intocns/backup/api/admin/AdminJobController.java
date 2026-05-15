package com.intocns.backup.api.admin;

import com.intocns.backup.api.error.ErrorCode;
import com.intocns.backup.api.error.ErrorResponse;
import com.intocns.backup.application.job.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Tag(name = "Admin - Job", description = "배치 잡 수동 트리거 (X-Admin-Key 필요)")
@SecurityRequirement(name = "adminKey")
@RestController
@RequestMapping("/admin/jobs")
public class AdminJobController {

    private final Map<String, Runnable> jobs;

    public AdminJobController(
            ExpiredSessionCleanupJob expiredSessionCleanupJob,
            RetentionPolicyJob retentionPolicyJob,
            QuotaRebalanceJob quotaRebalanceJob,
            IntegrityVerificationJob integrityVerificationJob,
            TrashCleanupJob trashCleanupJob) {

        jobs = new LinkedHashMap<>();
        jobs.put("ExpiredSessionCleanupJob", expiredSessionCleanupJob::run);
        jobs.put("RetentionPolicyJob", retentionPolicyJob::run);
        jobs.put("QuotaRebalanceJob", quotaRebalanceJob::run);
        jobs.put("IntegrityVerificationJob", integrityVerificationJob::run);
        jobs.put("TrashCleanupJob", trashCleanupJob::run);
    }

    @Operation(
        summary = "사용 가능한 잡 목록 조회",
        description = "수동으로 실행 가능한 배치 잡 이름 목록을 반환합니다."
    )
    @GetMapping
    public Set<String> listJobs() {
        return jobs.keySet();
    }

    @Operation(
        summary = "배치 잡 수동 실행",
        description = "지정한 잡을 즉시 실행합니다. 잡은 동기적으로 실행되며 결과는 job_execution_log 테이블에 기록됩니다."
    )
    @ApiResponse(responseCode = "204", description = "실행 요청 완료")
    @ApiResponse(responseCode = "404", description = "잡 없음 (code: 1013)")
    @PostMapping("/{jobName}/run")
    public ResponseEntity<?> runJob(
            @Parameter(description = "잡 이름 (예: TrashCleanupJob)") @PathVariable String jobName) {

        Runnable job = jobs.get(jobName);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of(ErrorCode.JOB_NOT_FOUND,
                            "Job not found: " + jobName + ". Available: " + jobs.keySet()));
        }

        job.run();
        return ResponseEntity.noContent().build();
    }
}
