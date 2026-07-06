package com.praxis.intake.internal;

import com.praxis.intake.config.IntakeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts an uploaded .zip into the workspace, defending against the two
 * classic archive attacks:
 *   1) Zip-slip / path traversal: an entry named "../../etc/passwd" — we
 *      reject any entry that would resolve outside the workspace.
 *   2) Zip bomb: a tiny compressed entry that explodes to gigabytes — we cap
 *      per-entry size and the compression ratio, and the running total is
 *      re-checked by JavaFileScanner afterwards.
 */
@Component
public class ZipExtractor {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractor.class);
    private static final byte[] BUFFER_HINT = new byte[8192];

    private final IntakeProperties properties;

    public ZipExtractor(IntakeProperties properties) {
        this.properties = properties;
    }

    public void extractInto(String zipPath, Path workspace) {
        Path zip = Path.of(zipPath);
        if (!Files.isRegularFile(zip)) {
            throw new FetchFailedException("Zip file not found: " + zipPath, null);
        }

        Path root = workspace.toAbsolutePath().normalize();
        long totalUncompressed = 0;

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path target = root.resolve(entry.getName()).normalize();

                if (!target.startsWith(root)) {
                    throw new UnsafeArchiveException("Zip entry escapes workspace: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                assertNotBomb(entry);
                totalUncompressed += Math.max(entry.getSize(), 0);
                if (totalUncompressed > properties.maxTotalSizeBytes()) {
                    throw new SourceTooLargeException(
                            "Zip expands beyond the " + properties.getMaxTotalSizeMb() + " MB total limit.");
                }

                Files.createDirectories(target.getParent());
                copy(zipFile, entry, target);
            }
        } catch (IOException e) {
            throw new FetchFailedException("Failed to extract zip: " + zipPath, e);
        }
        log.info("Extracted {} into {}", zipPath, workspace);
    }

    private void assertNotBomb(ZipEntry entry) {
        long compressed = entry.getCompressedSize();
        long uncompressed = entry.getSize();
        if (compressed > 0 && uncompressed > 0) {
            long ratio = uncompressed / compressed;
            if (ratio > properties.getMaxCompressionRatio()) {
                throw new UnsafeArchiveException(
                        "Suspicious compression ratio (" + ratio + ":1) for entry " + entry.getName());
            }
        }
        if (uncompressed > properties.maxFileSizeBytes() * 50) {
            // guard against entries that lie about size; the 50x slack covers legit large-but-not-insane files
            throw new UnsafeArchiveException("Zip entry declares an implausible size: " + entry.getName());
        }
    }

    private void copy(ZipFile zipFile, ZipEntry entry, Path target) throws IOException {
        try (var in = zipFile.getInputStream(entry);
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            byte[] buffer = new byte[BUFFER_HINT.length];
            int read;
            long written = 0;
            long cap = properties.maxFileSizeBytes() * 50;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                written += read;
                if (written > cap) {
                    throw new UnsafeArchiveException("Zip entry exceeded its declared size while extracting: "
                            + entry.getName());
                }
            }
        }
    }
}
