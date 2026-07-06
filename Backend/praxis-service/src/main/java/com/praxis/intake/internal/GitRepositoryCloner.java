package com.praxis.intake.internal;

import com.praxis.intake.config.IntakeProperties;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Clones a public GitHub repository into the workspace. Shallow (depth 1) so we
 * pull the current tree, not years of history — faster and smaller. A guard
 * rejects obviously non-http(s) refs to avoid file:// / ssh tricks against the
 * server. Private-repo auth (OAuth tokens) is a Phase-2 concern.
 */
@Component
public class GitRepositoryCloner {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryCloner.class);

    private final IntakeProperties properties;

    public GitRepositoryCloner(IntakeProperties properties) {
        this.properties = properties;
    }

    public void cloneInto(String repoUrl, Path workspace) {
        if (repoUrl == null || !(repoUrl.startsWith("https://") || repoUrl.startsWith("http://"))) {
            throw new FetchFailedException("Only http(s) git URLs are supported: " + repoUrl, null);
        }

        long deadline = System.currentTimeMillis() + properties.getCloneTimeoutSeconds() * 1000L;
        Thread cloneThread = Thread.currentThread();

        // JGit has no direct timeout on the whole clone; we interrupt from a watchdog.
        Thread watchdog = new Thread(() -> {
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            cloneThread.interrupt();
        }, "clone-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(workspace.toFile())
                .setDepth(1)
                .setCloneAllBranches(false)
                .call()) {
            log.info("Cloned {} into {}", repoUrl, workspace);
        } catch (Exception e) {
            throw new FetchFailedException("Failed to clone repository: " + repoUrl, e);
        } finally {
            watchdog.interrupt();
        }
    }
}
