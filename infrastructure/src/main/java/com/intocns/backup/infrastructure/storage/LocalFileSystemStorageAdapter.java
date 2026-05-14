package com.intocns.backup.infrastructure.storage;

import com.intocns.backup.domain.model.BackupType;
import com.intocns.backup.domain.model.HospitalId;
import com.intocns.backup.domain.port.BackupStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Component
public class LocalFileSystemStorageAdapter implements BackupStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorageAdapter.class);

    private final Path artifactsRoot;
    private final Path trashRoot;

    public LocalFileSystemStorageAdapter(
            @Value("${backup.storage.artifacts-root}") String artifactsRoot,
            @Value("${backup.storage.trash-root}") String trashRoot) throws IOException {
        this.artifactsRoot = Path.of(artifactsRoot);
        this.trashRoot = Path.of(trashRoot);
        Files.createDirectories(this.artifactsRoot);
        Files.createDirectories(this.trashRoot);
    }

    @Override
    public Path promoteToArtifacts(
            Path incomingDataPath, HospitalId hospitalId, BackupType type, String filename) throws IOException {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Path target = artifactsRoot
                .resolve(String.valueOf(hospitalId.cocode()))
                .resolve(type.name().toLowerCase())
                .resolve(String.valueOf(today.getYear()))
                .resolve(String.format("%02d", today.getMonthValue()))
                .resolve(String.format("%02d", today.getDayOfMonth()))
                .resolve(Instant.now().toEpochMilli() + "_" + sanitize(filename));

        Files.createDirectories(target.getParent());
        Files.move(incomingDataPath, target, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    @Override
    public void moveToTrash(Path artifactPath) throws IOException {
        Path trashTarget = trashRoot
                .resolve(LocalDate.now(ZoneOffset.UTC).toString())
                .resolve(artifactPath.getFileName());

        Files.createDirectories(trashTarget.getParent());
        try {
            Files.move(artifactPath, trashTarget, StandardCopyOption.ATOMIC_MOVE);
        } catch (NoSuchFileException e) {
            // 파일이 이미 없는 경우 — DB 정리(markPurged, subtractUsage)는 계속 진행
            log.warn("moveToTrash skipped, file not found: {}", artifactPath);
        }
    }

    @Override
    public String sha256(Path path) throws IOException {
        MessageDigest digest = newSha256Digest();
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sanitize(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
