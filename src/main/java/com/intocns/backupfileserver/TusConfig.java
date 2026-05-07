package com.intocns.backupfileserver;

import me.desair.tus.server.TusFileUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class TusConfig {

    @Bean
    public TusFileUploadService tusFileUploadService(
            @Value("${backup.storage.incoming-root}") String incomingRoot,
            @Value("${backup.tus.max-upload-size}") long maxUploadSize) throws IOException {

        Files.createDirectories(Path.of(incomingRoot));

        return new TusFileUploadService()
                .withUploadUri("/files")
                .withStoragePath(incomingRoot)
                .withMaxUploadSize(maxUploadSize)
                .withDownloadFeature();
    }
}
