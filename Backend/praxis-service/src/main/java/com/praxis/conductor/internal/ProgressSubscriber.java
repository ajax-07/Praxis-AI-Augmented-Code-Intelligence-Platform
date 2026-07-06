package com.praxis.conductor.internal;


import com.praxis.conductor.api.dto.ProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Receives ProgressEvents fanned out over Redis Pub/Sub (from ANY instance's
 * ProgressPublisher) and hands them to this instance's SseEmitterRegistry,
 * which delivers to whatever browsers are connected here.
 */
@Component
public class ProgressSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ProgressSubscriber.class);

    private final SseEmitterRegistry registry;
    private final ObjectMapper objectMapper;

    public ProgressSubscriber(SseEmitterRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ProgressEvent event = objectMapper.readValue(json, ProgressEvent.class);
            registry.broadcast(event);
        } catch (Exception ex) {
            log.warn("Failed to relay progress message", ex);
        }
    }
}
