package com.praxis.intake.internal;

import com.praxis.common.SourceType;
import com.praxis.intake.api.UploadStore;
import com.praxis.intake.api.dto.StagedSource;
import com.praxis.intake.config.IntakeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Stages uploads into {workspaceRoot}/_uploads/{uuid}.{ext}.
 *
 * Trust model: the client controls the filename and the bytes, so neither is
 * trusted. The filename is used only to pick the format (against the
 * allow-list); the staged file always gets a random name. Bytes are counted
 * while streaming and cut off at the size cap — Content-Length can lie.
 * Content-level safety (zip-slip, zip-bomb) is enforced later by ZipExtractor,
 * exactly as for any other zip sourceRef.
 */
@Service
public class UploadStoreImpl implements UploadStore {

    private static final Logger log = LoggerFactory.getLogger(UploadStoreImpl.class);

    private final IntakeProperties properties;

    public UploadStoreImpl(IntakeProperties properties) {
        this.properties = properties;
    }

    @Override
    public StagedSource stage(String originalFilename, InputStream content) {
        String extension = matchAllowedExtension(originalFilename);
        SourceType sourceType = sourceTypeFor(extension);

        Path staged = createStagingFile(extension);
        long written = copyCapped(content, staged);
        if (written == 0) {
            deleteQuietly(staged);
            throw new InvalidUploadException("EMPTY_UPLOAD", "The uploaded file is empty");
        }

        log.info("Staged upload '{}' as {} ({} bytes, type {})",
                originalFilename, staged.getFileName(), written, sourceType);
        return new StagedSource(staged.toAbsolutePath().toString(), sourceType, originalFilename);
    }

    /** The filename decides the format — and only formats we can fetch are allowed in. */
    private String matchAllowedExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidUploadException("UNSUPPORTED_FILE_TYPE", "Upload has no filename");
        }
        String lower = originalFilename.toLowerCase(Locale.ROOT);
        return properties.getAllowedUploadExtensions().stream()
                .filter(ext -> lower.endsWith("." + ext))
                .findFirst()
                .orElseThrow(() -> new InvalidUploadException("UNSUPPORTED_FILE_TYPE",
                        "Unsupported file type — allowed: " + properties.getAllowedUploadExtensions()));
    }

    /**
     * Extension → SourceType is THE seam for new formats: add the extension to
     * the allow-list config, map it here, and provide a fetcher for the type.
     */
    private SourceType sourceTypeFor(String extension) {
        return switch (extension) {
            case "zip" -> SourceType.ZIP;
            default -> throw new InvalidUploadException("UNSUPPORTED_FILE_TYPE",
                    "No fetcher exists for ." + extension + " uploads yet");
        };
    }

    private Path createStagingFile(String extension) {
        Path dir = properties.uploadRoot();
        try {
            Files.createDirectories(dir);
            return dir.resolve(UUID.randomUUID() + "." + extension);
        } catch (IOException e) {
            throw new FetchFailedException("Could not create upload staging directory " + dir, e);
        }
    }

    /** Streams to disk counting bytes; aborts (and cleans up) past the cap. */
    private long copyCapped(InputStream in, Path target) {
        long cap = properties.maxTotalSizeBytes();
        long written = 0;
        byte[] buffer = new byte[8192];
        try (in; OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > cap) {
                    throw new InvalidUploadException("UPLOAD_TOO_LARGE",
                            "Upload exceeds " + properties.getMaxTotalSizeMb() + " MB");
                }
                out.write(buffer, 0, read);
            }
            return written;
        } catch (IOException e) {
            deleteQuietly(target);
            throw new FetchFailedException("Failed to store the uploaded file", e);
        } catch (InvalidUploadException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    private void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("Could not remove partial upload {} — janitor will reclaim it", p, e);
        }
    }
}
