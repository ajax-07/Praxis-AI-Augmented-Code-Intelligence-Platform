package com.praxis.intake.internal;

import com.praxis.intake.api.dto.JavaFile;
import com.praxis.intake.config.IntakeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Walks a materialized workspace and returns only the .java files worth
 * analyzing, enforcing the count/size guards as it goes.
 *
 * NOTE on .gitignore: a git CLONE only contains committed files, so ignored
 * build output isn't present anyway. For ZIP uploads (which may include a whole
 * working dir) we skip the well-known build/output/vcs directories below. Full
 * .gitignore parsing is deliberately deferred to Phase 2 — this covers the 99%.
 */
@Component
public class JavaFileScanner {

    private static final Logger log = LoggerFactory.getLogger(JavaFileScanner.class);

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "target", "build", "out", "bin", "node_modules", ".idea", ".gradle");

    private final IntakeProperties properties;

    public JavaFileScanner(IntakeProperties properties) {
        this.properties = properties;
    }

    public List<JavaFile> scan(Path workspace) {
        List<JavaFile> files = new ArrayList<>();
        long totalBytes = 0;

        try (Stream<Path> walk = Files.walk(workspace)) {
            List<Path> candidates = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> isNotInIgnoredDir(workspace, p))
                    .toList();

            for (Path p : candidates) {
                long size = Files.size(p);

                if (size > properties.maxFileSizeBytes()) {
                    log.debug("Skipping oversized file {} ({} bytes)", p, size);
                    continue; // a single giant "file" is almost never real source
                }

                totalBytes += size;
                if (totalBytes > properties.maxTotalSizeBytes()) {
                    throw new SourceTooLargeException(
                            "Source exceeds the " + properties.getMaxTotalSizeMb() + " MB total limit.");
                }

                String relative = workspace.relativize(p).toString().replace('\\', '/');
                files.add(new JavaFile(relative, p, size));

                if (files.size() > properties.getMaxFiles()) {
                    throw new SourceTooLargeException(
                            "Source exceeds the " + properties.getMaxFiles() + " .java file limit.");
                }
            }
        } catch (IOException e) {
            throw new FetchFailedException("Failed to scan workspace " + workspace, e);
        }

        log.info("Scanned workspace {}: {} java files, {} bytes", workspace, files.size(), totalBytes);
        return files;
    }

    private boolean isNotInIgnoredDir(Path workspace, Path file) {
        Path relative = workspace.relativize(file);
        for (Path segment : relative) {
            if (IGNORED_DIRS.contains(segment.toString())) {
                return false;
            }
        }
        return true;
    }
}
