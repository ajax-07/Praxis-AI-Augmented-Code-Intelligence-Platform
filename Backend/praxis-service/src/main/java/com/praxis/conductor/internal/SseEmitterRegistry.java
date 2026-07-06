package com.praxis.conductor.internal;

import com.praxis.conductor.api.dto.ProgressEvent;
import com.praxis.conductor.domain.AnalysisStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the live SSE connections THIS app instance is serving, keyed by
 * analysisId. When a progress event arrives (from Redis Pub/Sub — possibly
 * originating on another instance), we look up any local emitters for that
 * analysis and push to them. Emitters on other instances are handled by
 * their own registries listening to the same Redis channel.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 min safety cap

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID analysisId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(analysisId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(analysisId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /**
     * Immediately push the analysis's CURRENT status to a freshly-registered
     * emitter, and complete the stream if the analysis is already finished.
     * Without this, a client that subscribes AFTER the pipeline has already run
     * (or after it completed) would never receive a terminal event and would
     * hang open until the 30-minute timeout.
     */
    public void sendCurrent(SseEmitter emitter, UUID analysisId, AnalysisStatus status) {
        try {
            emitter.send(SseEmitter.event().name("progress")
                    .data(ProgressEvent.of(analysisId, status, "Current status: " + status)));
            if (status.isTerminal()) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException ex) {
            log.debug("Initial SSE send failed for analysis {}", analysisId);
            remove(analysisId, emitter);
        }
    }

    public void broadcast(ProgressEvent event) {
        List<SseEmitter> targets = emitters.get(event.analysisId());
        if (targets == null) {
            return; // no one on this instance is watching this analysis
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(event));
                if (event.status().isTerminal()) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException ex) {
                log.debug("Dropping dead SSE emitter for analysis {}", event.analysisId());
                remove(event.analysisId(), emitter);
            }
        }
    }

    private void remove(UUID analysisId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(analysisId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(analysisId);
            }
        }
    }
}
