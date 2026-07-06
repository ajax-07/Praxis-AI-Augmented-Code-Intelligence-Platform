package com.praxis.intake.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The safety envelope for fetching untrusted code. Every limit here exists to
 * stop a malicious or accidentally-huge source from exhausting disk, memory,
 * or time. Bound from praxis.intake.* in application.yml.
 */
@ConfigurationProperties(prefix = "praxis.intake")
public class IntakeProperties {

    /** Root directory under which each analysis gets its own workspace. */
    private String workspaceRoot = System.getProperty("java.io.tmpdir") + "/praxis-workspaces";

    /** Reject a source whose extracted .java files exceed this count. */
    private int maxFiles = 5000;

    /** Reject a source whose total extracted size exceeds this (MB). */
    private int maxTotalSizeMb = 200;

    /** Skip any single file larger than this (MB) — real source files are tiny. */
    private int maxFileSizeMb = 2;

    /** Abort a git clone that runs longer than this. */
    private int cloneTimeoutSeconds = 120;

    /**
     * Zip-bomb guard: reject a zip entry whose uncompressed/compressed size
     * ratio exceeds this. Normal source zips are well under 100:1.
     */
    private int maxCompressionRatio = 120;

    public String getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
    public int getMaxFiles() { return maxFiles; }
    public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }
    public int getMaxTotalSizeMb() { return maxTotalSizeMb; }
    public void setMaxTotalSizeMb(int maxTotalSizeMb) { this.maxTotalSizeMb = maxTotalSizeMb; }
    public int getMaxFileSizeMb() { return maxFileSizeMb; }
    public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
    public int getCloneTimeoutSeconds() { return cloneTimeoutSeconds; }
    public void setCloneTimeoutSeconds(int cloneTimeoutSeconds) { this.cloneTimeoutSeconds = cloneTimeoutSeconds; }
    public int getMaxCompressionRatio() { return maxCompressionRatio; }
    public void setMaxCompressionRatio(int maxCompressionRatio) { this.maxCompressionRatio = maxCompressionRatio; }

    public long maxTotalSizeBytes() { return (long) maxTotalSizeMb * 1024 * 1024; }
    public long maxFileSizeBytes() { return (long) maxFileSizeMb * 1024 * 1024; }
}
