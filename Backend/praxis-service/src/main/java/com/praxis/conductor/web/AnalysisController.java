package com.praxis.conductor.web;

import com.praxis.conductor.api.dto.AnalysisView;
import com.praxis.conductor.api.dto.StartAnalysisRequest;
import com.praxis.conductor.api.dto.StartAnalysisResponse;
import com.praxis.conductor.internal.AnalysisOrchestrator;
import com.praxis.conductor.internal.SseEmitterRegistry;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * The async contract:
 *   POST /analyses          -> 202 Accepted + {analysisId}   (returns instantly)
 *   GET  /analyses/{id}      -> current status (for polling / page refresh)
 *   GET  /analyses/{id}/events -> SSE stream of live stage progress
 * All three are tenant-scoped via the JWT (SecurityConfig requires auth).
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController {

    private final AnalysisOrchestrator orchestrator;
    private final SseEmitterRegistry sseRegistry;

    public AnalysisController(AnalysisOrchestrator orchestrator, SseEmitterRegistry sseRegistry) {
        this.orchestrator = orchestrator;
        this.sseRegistry = sseRegistry;
    }

    @PostMapping
    public ResponseEntity<StartAnalysisResponse> start(@Valid @RequestBody StartAnalysisRequest request) {
        return ResponseEntity.accepted().body(orchestrator.start(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(orchestrator.get(id));
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id) {
        // Verify the analysis exists and belongs to this tenant before streaming.
        AnalysisView current = orchestrator.get(id);
        SseEmitter emitter = sseRegistry.register(id);
        // Push the current status right away so a late subscriber isn't left
        // hanging, and close the stream immediately if the analysis is done.
        sseRegistry.sendCurrent(emitter, id, current.status());
        return emitter;
    }
}
