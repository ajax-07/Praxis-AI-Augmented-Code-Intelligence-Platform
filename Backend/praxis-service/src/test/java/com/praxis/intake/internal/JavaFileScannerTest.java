package com.praxis.intake.internal;

import com.praxis.intake.api.dto.JavaFile;
import com.praxis.intake.config.IntakeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure filesystem test — no Spring, no network, no DB. Builds a fake workspace
 * on disk and asserts the scanner isolates .java, ignores build/vcs dirs, and
 * enforces the guards.
 */
class JavaFileScannerTest {

    private IntakeProperties props() {
        return new IntakeProperties();
    }

    @Test
    void keepsOnlyJavaFilesAndIgnoresBuildAndVcsDirs(@TempDir Path ws) throws IOException {
        Files.writeString(ws.resolve("A.java"), "class A {}");
        Files.createDirectories(ws.resolve("src/main/java/com"));
        Files.writeString(ws.resolve("src/main/java/com/B.java"), "class B {}");
        Files.writeString(ws.resolve("README.md"), "# not code");
        Files.createDirectories(ws.resolve("target/classes"));
        Files.writeString(ws.resolve("target/classes/A.class"), "bytecode");
        Files.writeString(ws.resolve("target/Generated.java"), "class G {}"); // in build dir -> ignored
        Files.createDirectories(ws.resolve(".git"));
        Files.writeString(ws.resolve(".git/config.java"), "trick"); // in .git -> ignored

        List<JavaFile> files = new JavaFileScanner(props()).scan(ws);

        assertThat(files).extracting(JavaFile::relativePath)
                .containsExactlyInAnyOrder("A.java", "src/main/java/com/B.java");
    }

    @Test
    void skipsFilesLargerThanTheSingleFileLimit(@TempDir Path ws) throws IOException {
        IntakeProperties p = props();
        p.setMaxFileSizeMb(0); // 0 MB -> everything is "too big"
        Files.writeString(ws.resolve("Big.java"), "class Big {}");

        assertThat(new JavaFileScanner(p).scan(ws)).isEmpty();
    }

    @Test
    void rejectsTooManyFiles(@TempDir Path ws) throws IOException {
        IntakeProperties p = props();
        p.setMaxFiles(2);
        for (int i = 0; i < 5; i++) {
            Files.writeString(ws.resolve("C" + i + ".java"), "class C" + i + " {}");
        }
        assertThatThrownBy(() -> new JavaFileScanner(p).scan(ws))
                .isInstanceOf(SourceTooLargeException.class);
    }
}
