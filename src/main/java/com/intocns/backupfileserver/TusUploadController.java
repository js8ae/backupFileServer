package com.intocns.backupfileserver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.desair.tus.server.TusFileUploadService;
import me.desair.tus.server.exception.TusException;
import me.desair.tus.server.upload.UploadInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/files")
public class TusUploadController {

    private static final Logger log = LoggerFactory.getLogger(TusUploadController.class);

    private final TusFileUploadService tus;

    public TusUploadController(TusFileUploadService tus) {
        this.tus = tus;
    }

    @RequestMapping(
        value = {"", "/**"},
        method = {
            RequestMethod.POST,
            RequestMethod.PATCH,
            RequestMethod.HEAD,
            RequestMethod.OPTIONS,
            RequestMethod.DELETE,
            RequestMethod.GET
        }
    )
    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            tus.process(req, res);

            if ("PATCH".equalsIgnoreCase(req.getMethod())) {
                String uri = req.getRequestURI();
                UploadInfo info = tus.getUploadInfo(uri);

                if (info != null) {
                    if (info.isUploadInProgress()) {
                        log.info("[TUS] In-progress: id={} offset={}/{}",
                                uri, info.getOffset(), info.getLength());
                    } else {
                        log.info("[TUS] COMPLETED: id={} size={} metadata={}",
                                uri, info.getLength(), info.getMetadata());
                    }
                }
            }
        } catch (TusException e) {
            log.error("[TUS] error: {}", e.getMessage(), e);
            throw new IOException(e);
        }
    }
}
