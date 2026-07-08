package com.praxis.intake.internal;

import com.praxis.intake.config.IntakeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UploadJanitorTest {

    @Test
    void sweepRemovesExpiredUploadsAndKeepsFreshOnes(@TempDir Path root) throws IOException {
        IntakeProperties p = new IntakeProperties();
        p.setWorkspaceRoot(root.toString());
        p.setUploadRetentionHours(24);

        Path uploads = Files.createDirectories(root.resolve("_uploads"));
        Path expired = Files.writeString(uploads.resolve("old.zip"), "x");
        Files.setLastModifiedTime(expired,
                FileTime.from(Instant.now().minus(48, ChronoUnit.HOURS)));
        Path fresh = Files.writeString(uploads.resolve("fresh.zip"), "x");

        new UploadJanitor(p).sweep();

        assertThat(Files.exists(expired)).isFalse();
        assertThat(Files.exists(fresh)).isTrue();
    }

    @Test
    void sweepIsANoOpWhenNothingWasEverUploaded(@TempDir Path root) {
        IntakeProperties p = new IntakeProperties();
        p.setWorkspaceRoot(root.toString());
        new UploadJanitor(p).sweep(); // must not throw on a missing staging dir
    }
}
