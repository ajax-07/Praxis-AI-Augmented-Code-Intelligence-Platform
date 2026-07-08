package com.praxis.intake.internal;

import com.praxis.intake.config.IntakeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/**
 * Reclaims staged uploads. Retention-based (rather than delete-after-fetch)
 * on purpose: a redelivered Redis job must be able to re-read its zip, and a
 * worker crash must never leak files forever. Anything in the staging dir
 * older than the retention window is gone regardless of what happened to its
 * analysis.
 */
@Component
public class UploadJanitor {

    private static final Logger log = LoggerFactory.getLogger(UploadJanitor.class);

    private final IntakeProperties properties;

    public UploadJanitor(IntakeProperties properties) {
        this.properties = properties;
    }

    /** Hourly is plenty — staged zips are small in number, retention is in hours. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void sweep() {
        Path dir = properties.uploadRoot();
        if (!Files.isDirectory(dir)) {
            return; // nothing ever uploaded
        }
        Instant cutoff = Instant.now().minus(properties.getUploadRetentionHours(), ChronoUnit.HOURS);
        int removed = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (isOlderThan(file, cutoff) && deleteQuietly(file)) {
                    removed++;
                }
            }
        } catch (IOException e) {
            log.warn("Upload janitor could not list {}", dir, e);
        }
        if (removed > 0) {
            log.info("Upload janitor removed {} expired staged upload(s)", removed);
        }
    }

    private boolean isOlderThan(Path file, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false; // can't stat it now — next sweep will retry
        }
    }

    private boolean deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            log.warn("Upload janitor could not delete {} — will retry next sweep", file, e);
            return false;
        }
    }
}
