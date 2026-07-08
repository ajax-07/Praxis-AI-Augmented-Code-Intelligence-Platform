package com.praxis.intake.internal;

import com.praxis.common.SourceType;
import com.praxis.intake.api.dto.StagedSource;
import com.praxis.intake.config.IntakeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadStoreImplTest {

    private UploadStoreImpl store(Path root) {
        IntakeProperties p = new IntakeProperties();
        p.setWorkspaceRoot(root.toString());
        return new UploadStoreImpl(p);
    }

    @Test
    void stagesAZipUnderARandomNameAndMapsItToZipSourceType(@TempDir Path root) throws IOException {
        StagedSource staged = store(root).stage("My Project.ZIP",
                new ByteArrayInputStream("fake zip bytes".getBytes()));

        assertThat(staged.sourceType()).isEqualTo(SourceType.ZIP);
        assertThat(staged.originalFilename()).isEqualTo("My Project.ZIP");
        Path stagedFile = Path.of(staged.sourceRef());
        assertThat(Files.readString(stagedFile)).isEqualTo("fake zip bytes");
        // Client filename must never influence where the file lands.
        assertThat(stagedFile.getParent()).isEqualTo(root.resolve("_uploads"));
        assertThat(stagedFile.getFileName().toString()).doesNotContain("My Project");
    }

    @Test
    void rejectsDisallowedExtensions(@TempDir Path root) {
        assertThatThrownBy(() -> store(root).stage("evil.exe", new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(InvalidUploadException.class)
                .hasFieldOrPropertyWithValue("code", "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void rejectsEmptyUploadsAndLeavesNothingBehind(@TempDir Path root) throws IOException {
        assertThatThrownBy(() -> store(root).stage("empty.zip", new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(InvalidUploadException.class)
                .hasFieldOrPropertyWithValue("code", "EMPTY_UPLOAD");
        try (var files = Files.list(root.resolve("_uploads"))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void cutsOffPastTheSizeCapAndCleansUpThePartialFile(@TempDir Path root) throws IOException {
        IntakeProperties p = new IntakeProperties();
        p.setWorkspaceRoot(root.toString());
        p.setMaxTotalSizeMb(0); // cap = 0 bytes: any content is too large
        UploadStoreImpl store = new UploadStoreImpl(p);

        assertThatThrownBy(() -> store.stage("big.zip", new ByteArrayInputStream(new byte[10])))
                .isInstanceOf(InvalidUploadException.class)
                .hasFieldOrPropertyWithValue("code", "UPLOAD_TOO_LARGE");
        try (var files = Files.list(root.resolve("_uploads"))) {
            assertThat(files).isEmpty();
        }
    }
}
