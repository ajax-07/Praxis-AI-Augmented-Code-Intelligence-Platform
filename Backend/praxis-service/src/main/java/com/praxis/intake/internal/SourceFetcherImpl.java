package com.praxis.intake.internal;

import com.praxis.intake.api.SourceFetcher;
import com.praxis.intake.api.dto.FetchCommand;
import com.praxis.intake.api.dto.FetchResult;
import com.praxis.intake.api.dto.JavaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * The public Intake service. Orchestrates: make a clean workspace -> materialize
 * the source (clone or unzip) -> scan+filter+guard -> return the .java files.
 * If ANY step throws, the half-built workspace is deleted before the exception
 * propagates, so a failed fetch never leaks disk.
 */
@Service
public class SourceFetcherImpl implements SourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(SourceFetcherImpl.class);

    private final WorkspaceManager workspaceManager;
    private final GitRepositoryCloner gitCloner;
    private final ZipExtractor zipExtractor;
    private final JavaFileScanner scanner;

    public SourceFetcherImpl(
            WorkspaceManager workspaceManager,
            GitRepositoryCloner gitCloner,
            ZipExtractor zipExtractor,
            JavaFileScanner scanner
    ) {
        this.workspaceManager = workspaceManager;
        this.gitCloner = gitCloner;
        this.zipExtractor = zipExtractor;
        this.scanner = scanner;
    }

    @Override
    public FetchResult fetch(FetchCommand command) {
        Path workspace = workspaceManager.create(command.analysisId());
        try {
            switch (command.sourceType()) {
                case GITHUB -> gitCloner.cloneInto(command.sourceRef(), workspace);
                case ZIP -> zipExtractor.extractInto(command.sourceRef(), workspace);
            }

            List<JavaFile> files = scanner.scan(workspace);
            if (files.isEmpty()) {
                throw new FetchFailedException(
                        "No .java files found in the source — is it a Java project?", null);
            }
            log.info("Fetch complete for analysis {}: {} java files", command.analysisId(), files.size());
            return new FetchResult(workspace, files);

        } catch (RuntimeException e) {
            workspaceManager.delete(workspace); // don't leak a half-built workspace on failure
            throw e;
        }
    }

    @Override
    public void release(FetchResult result) {
        if (result != null) {
            workspaceManager.delete(result.workspacePath());
        }
    }
}
