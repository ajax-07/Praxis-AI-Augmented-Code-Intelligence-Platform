package com.praxis.intake.api;

import com.praxis.intake.api.dto.FetchCommand;
import com.praxis.intake.api.dto.FetchResult;

/**
 * The one thing Intake exposes: give me a source, I give you .java files in a
 * sandboxed workspace — and I clean it up when you tell me to.
 *
 * Implementations enforce hard safety limits (size, file count, timeouts,
 * zip-bomb protection). fetch() throws an unchecked IntakeException on any
 * violation; the caller's pipeline turns that into a FAILED analysis.
 */
public interface SourceFetcher {

    FetchResult fetch(FetchCommand command);

    /** Delete the ephemeral workspace produced by a prior fetch. Safe to call twice. */
    void release(FetchResult result);
}
