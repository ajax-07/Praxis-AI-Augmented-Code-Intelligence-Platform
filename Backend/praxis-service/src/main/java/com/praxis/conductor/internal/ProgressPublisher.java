package com.praxis.conductor.internal;

import com.praxis.conductor.api.dto.ProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes a ProgressEvent to a Redis Pub/Sub channel. Why Pub/Sub and not
 * just the local registry directly? Because the WORKER running the job and the
 * WEB instance holding the browser's SSE connection may be different JVMs once
 * you scale horizontally. Pub/Sub fans the event out to every instance; each
 * instance's SseEmitterRegistry then delivers to whichever browsers it holds.
 */
@Component
public class ProgressPublisher {

    static final String CHANNEL = "praxis:analysis:progress";

    private static final Logger log = LoggerFactory.getLogger(ProgressPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ProgressPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(ProgressEvent event) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            // Progress is best-effort telemetry; never fail the pipeline over it.
            log.warn("Failed to publish progress for analysis {}", event.analysisId(), ex);
        }
    }
}
