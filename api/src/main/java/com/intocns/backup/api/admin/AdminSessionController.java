package com.intocns.backup.api.admin;

import com.intocns.backup.application.AbortUploadUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/admin/sessions")
public class AdminSessionController {

    private final AbortUploadUseCase abortUpload;

    public AdminSessionController(AbortUploadUseCase abortUpload) {
        this.abortUpload = abortUpload;
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forceAbort(@PathVariable UUID sessionId) throws IOException {
        abortUpload.forceAbort(sessionId);
    }
}
