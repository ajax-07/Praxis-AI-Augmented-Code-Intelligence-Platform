package com.praxis.intake.internal;

import com.praxis.intake.api.dto.FetchCommand;
import com.praxis.intake.api.dto.FetchResult;
import com.praxis.common.SourceType;
import com.praxis.intake.config.IntakeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the whole ZIP fetch path locally (no network): build a zip, fetch
 * it, assert the .java files come back, then verify path-traversal is blocked.
 */
class ZipFetchTest {

    private SourceFetcherImpl fetcher(IntakeProperties p) {
        return new SourceFetcherImpl(
                new WorkspaceManager(p),
                new GitRepositoryCloner(p),
                new ZipExtractor(p),
                new JavaFileScanner(p));
    }

    private IntakeProperties propsRootedAt(Path root) {
        IntakeProperties p = new IntakeProperties();
        p.setWorkspaceRoot(root.resolve("ws").toString());
        return p;
    }

    @Test
    void extractsAndReturnsJavaFilesFromAZip(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("project.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            write(zos, "src/Main.java", "public class Main {}");
            write(zos, "src/Util.java", "class Util {}");
            write(zos, "notes.txt", "not code");
        }

        IntakeProperties p = propsRootedAt(tmp);
        FetchResult result = fetcher(p).fetch(
                new FetchCommand(UUID.randomUUID(), SourceType.ZIP, zip.toString()));

        assertThat(result.fileCount()).isEqualTo(2);
        assertThat(result.files()).extracting(f -> f.relativePath())
                .containsExactlyInAnyOrder("src/Main.java", "src/Util.java");

        // release deletes the workspace
        fetcher(p).release(result);
        assertThat(Files.exists(result.workspacePath())).isFalse();
    }

    @Test
    void blocksZipSlipPathTraversal(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("evil.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            write(zos, "../../escape.java", "class Escape {}");
        }
        IntakeProperties p = propsRootedAt(tmp);

        assertThatThrownBy(() -> fetcher(p).fetch(
                new FetchCommand(UUID.randomUUID(), SourceType.ZIP, zip.toString())))
                .isInstanceOf(UnsafeArchiveException.class);
    }

    @Test
    void failsClearlyWhenZipHasNoJavaFiles(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            write(zos, "readme.md", "docs only");
        }
        IntakeProperties p = propsRootedAt(tmp);

        assertThatThrownBy(() -> fetcher(p).fetch(
                new FetchCommand(UUID.randomUUID(), SourceType.ZIP, zip.toString())))
                .isInstanceOf(FetchFailedException.class);
    }

    private void write(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes());
        zos.closeEntry();
    }
}
