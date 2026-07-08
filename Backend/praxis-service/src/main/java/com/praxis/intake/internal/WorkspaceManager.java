package com.praxis.intake.internal;

import com.praxis.intake.config.IntakeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Owns the lifecycle of the ephemeral per-analysis workspace. Every fetch gets
 * a fresh directory; it is deleted when the pipeline releases it (success OR
 * failure). create() first wipes any stale directory for the same analysis so
 * a redelivered/retried job always starts clean.
 */
@Component
public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final IntakeProperties properties;

    public WorkspaceManager(IntakeProperties properties) {
        this.properties = properties;
    }

    public Path create(UUID analysisId) {
        Path dir = Path.of(properties.getWorkspaceRoot(), analysisId.toString());
        try {
            deleteRecursively(dir);        // clean slate for retries
            Files.createDirectories(dir);
            log.debug("Created workspace {}", dir);
            return dir;
        } catch (IOException e) {
            throw new FetchFailedException("Could not create workspace for analysis " + analysisId, e);
        }
    }

    public void delete(Path workspace) {
        if (workspace == null) {
            return;
        }
        try {
            deleteRecursively(workspace);
            log.debug("Deleted workspace {}", workspace);
        } catch (IOException e) {
            // Never fail the job over cleanup; log so an operator can reclaim disk.
            log.warn("Failed to delete workspace {} — manual cleanup may be needed", workspace, e);
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()) // children before parents
                .forEach(p -> {
                    try {
                        deleteForcing(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    /**
     * Git marks pack files (.git/objects/pack/*.pack) read-only, and Windows
     * refuses to delete read-only files. Clear the DOS read-only flag and retry
     * once; on filesystems without DOS attributes the original error stands.
     */
    private void deleteForcing(Path p) throws IOException {
        try {
            Files.delete(p);
        } catch (AccessDeniedException denied) {
            DosFileAttributeView dos = Files.getFileAttributeView(p, DosFileAttributeView.class);
            if (dos == null) {
                throw denied;
            }
            dos.setReadOnly(false);
            Files.delete(p);
        }
    }
}
