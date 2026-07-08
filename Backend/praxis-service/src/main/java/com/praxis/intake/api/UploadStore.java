package com.praxis.intake.api;

import com.praxis.intake.api.dto.StagedSource;

import java.io.InputStream;

/**
 * Receives an uploaded source archive, validates it (extension allow-list,
 * non-empty, size cap) and stages it on local disk so the normal fetch flow
 * can consume it via its sourceRef — the pipeline never knows whether a zip
 * arrived by upload or was already on disk.
 *
 * Staged files are ephemeral: {@code UploadJanitor} removes them once they are
 * older than praxis.intake.upload-retention-hours. Retention (rather than
 * delete-after-fetch) keeps redelivered jobs re-runnable and survives crashes.
 */
public interface UploadStore {

    /**
     * @param originalFilename the client-supplied filename — used ONLY to pick
     *                         the format; the staged file gets a random name
     * @param content          the upload byte stream; fully drained and closed here
     * @return where the file was staged and the SourceType to analyze it as
     */
    StagedSource stage(String originalFilename, InputStream content);
}
