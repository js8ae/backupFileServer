package com.intocns.backup.application.job;

import com.intocns.backup.domain.model.UploadSession;
import com.intocns.backup.domain.model.UploadStatus;
import com.intocns.backup.domain.port.ChunkedUploadProtocol;
import com.intocns.backup.domain.port.UploadSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class ExpiredSessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredSessionCleanupJob.class);

    private final UploadSessionRepository sessionRepository;
    private final ChunkedUploadProtocol protocol;

    public ExpiredSessionCleanupJob(UploadSessionRepository sessionRepository,
                                    ChunkedUploadProtocol protocol) {
        this.sessionRepository = sessionRepository;
        this.protocol = protocol;
    }

    @Scheduled(cron = "0 0 * * * *")  // 매 정시
    @Transactional
    public void run() {
        List<UploadSession> expired = sessionRepository.findExpiredBefore(Instant.now());
        if (expired.isEmpty()) {
            return;
        }

        log.info("job=ExpiredSessionCleanup expired_count={}", expired.size());

        int cleaned = 0;
        int failed = 0;
        for (UploadSession session : expired) {
            try {
                if (session.tusUploadUri() != null) {
                    protocol.deleteUpload(session.tusUploadUri());
                }
                sessionRepository.updateStatus(session.id(), UploadStatus.ABORTED);
                cleaned++;
            } catch (IOException e) {
                log.error("job=ExpiredSessionCleanup session_id={} error=tus_delete_failed msg={}",
                        session.id(), e.getMessage());
                failed++;
            }
        }

        log.info("job=ExpiredSessionCleanup done cleaned={} failed={}", cleaned, failed);
    }
}
