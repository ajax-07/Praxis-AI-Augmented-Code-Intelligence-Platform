package com.praxis.intake.internal;

import com.praxis.intake.config.IntakeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceManagerTest {

    @Test
    void createGivesAFreshDirAndDeleteRemovesItRecursively(@TempDir Path root) throws IOException {
        IntakeProperties p = new IntakeProperties();
        p.setWorkspaceRoot(root.toString());
        WorkspaceManager wm = new WorkspaceManager(p);
        UUID id = UUID.randomUUID();

        Path ws = wm.create(id);
        assertThat(Files.isDirectory(ws)).isTrue();

        Files.createDirectories(ws.resolve("nested"));
        Files.writeString(ws.resolve("nested/file.java"), "class X {}");

        wm.delete(ws);
        assertThat(Files.exists(ws)).isFalse();
    }

    @Test
    void createWipesAStaleWorkspaceForTheSameAnalysis(@TempDir Path root) throws IOException {
        IntakeProperties p = new IntakeProperties();
        p.setWorkspaceRoot(root.toString());
        WorkspaceManager wm = new WorkspaceManager(p);
        UUID id = UUID.randomUUID();

        Path first = wm.create(id);
        Files.writeString(first.resolve("stale.java"), "class Stale {}");

        Path second = wm.create(id); // retry: same id
        assertThat(second).isEqualTo(first);
        assertThat(Files.exists(second.resolve("stale.java"))).isFalse(); // clean slate
    }

    @Test
    void deleteRemovesReadOnlyFiles(@TempDir Path root) throws IOException {
        // Git marks .pack files read-only; on Windows a plain Files.delete()
        // throws AccessDeniedException for those. Cleanup must still succeed.
        IntakeProperties p = new IntakeProperties();
        p.setWorkspaceRoot(root.toString());
        WorkspaceManager wm = new WorkspaceManager(p);

        Path ws = wm.create(UUID.randomUUID());
        Path pack = Files.createDirectories(ws.resolve(".git/objects/pack"))
                .resolve("pack-abc.pack");
        Files.writeString(pack, "not a real pack");
        assertThat(pack.toFile().setReadOnly()).isTrue();

        wm.delete(ws);
        assertThat(Files.exists(ws)).isFalse();
    }
}
