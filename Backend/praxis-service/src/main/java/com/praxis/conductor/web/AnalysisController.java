package com.praxis.conductor.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.praxis.conductor.api.dto.AnalysisView;
import com.praxis.conductor.api.dto.StartAnalysisRequest;
import com.praxis.conductor.api.dto.StartAnalysisResponse;
import com.praxis.conductor.internal.AnalysisOrchestrator;
import com.praxis.conductor.internal.SseEmitterRegistry;
import com.praxis.intake.api.UploadStore;
import com.praxis.intake.api.dto.StagedSource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * The async contract:
 *   POST /analyses            -> 202 Accepted + {analysisId}   (returns instantly)
 *   POST /analyses/upload      -> same, but the source arrives as a multipart file
 *   GET  /analyses/{id}        -> current status (for polling / page refresh)
 *   GET  /analyses/{id}/events -> SSE stream of live stage progress
 * All tenant-scoped via the JWT (SecurityConfig requires auth).
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final AnalysisOrchestrator orchestrator;
    private final SseEmitterRegistry sseRegistry;
    private final UploadStore uploadStore;

    public AnalysisController(AnalysisOrchestrator orchestrator, SseEmitterRegistry sseRegistry,
                              UploadStore uploadStore) {
        this.orchestrator = orchestrator;
        this.sseRegistry = sseRegistry;
        this.uploadStore = uploadStore;
    }

    @PostMapping
    public ResponseEntity<StartAnalysisResponse> start(@Valid @RequestBody StartAnalysisRequest request) {
        log.info("HTTP POST /analyses name='{}'", request.name());
        return ResponseEntity.accepted().body(orchestrator.start(request));
    }

    /**
     * Upload-and-analyze in one call: intake stages the file (allow-list, size
     * cap, random name) and tells us the SourceType; from there it is exactly
     * the normal start() flow — the pipeline can't tell the difference.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StartAnalysisResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        log.info("HTTP POST /analyses/upload filename='{}' size={}B", file.getOriginalFilename(), file.getSize());

        StagedSource staged;
        try {
            staged = uploadStore.stage(file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }

        String analysisName = (name == null || name.isBlank()) ? defaultName(staged.originalFilename()) : name.trim();
        return ResponseEntity.accepted().body(orchestrator.start(
                new StartAnalysisRequest(analysisName, staged.sourceType(), staged.sourceRef())));
    }

    /** "my-project.zip" → "my-project"; a nameless upload still gets a usable label. */
    private static String defaultName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "uploaded-source";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisView> get(@PathVariable UUID id) {
        log.debug("HTTP GET /analyses/{}", id);
        return ResponseEntity.ok(orchestrator.get(id));
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id) {
        // Verify the analysis exists and belongs to this tenant before streaming.
        AnalysisView current = orchestrator.get(id);
        log.info("SSE subscribe for analysisId={} (current status {})", id, current.status());
        SseEmitter emitter = sseRegistry.register(id);
        // Push the current status right away so a late subscriber isn't left
        // hanging, and close the stream immediately if the analysis is done.
        sseRegistry.sendCurrent(emitter, id, current.status());
        return emitter;
    }
}
