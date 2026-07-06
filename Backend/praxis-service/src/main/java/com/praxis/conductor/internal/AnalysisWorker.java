package com.praxis.conductor.internal;

import com.praxis.conductor.config.ConductorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consumer side of the job stream. One message = one analysis to run. Runs on
 * the stream container's thread pool (see ConductorConfig), NOT on any web
 * request thread — this is where the minutes-long work happens.
 *
 * We manually ACK only after the pipeline finishes. If this instance crashes
 * mid-job, the message stays pending and can be reclaimed by another worker
 * (reclaim wiring is a Phase-2 hardening; the pipeline's idempotency guard
 * already makes re-processing safe).
 */
@Component
public class AnalysisWorker implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

    private final AnalysisPipeline pipeline;
    private final StringRedisTemplate redis;

    public AnalysisWorker(AnalysisPipeline pipeline, StringRedisTemplate redis) {
        this.pipeline = pipeline;
        this.redis = redis;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        Map<String, String> body = record.getValue();
        RecordId recordId = record.getId();
        try {
            UUID analysisId = UUID.fromString(body.get("analysisId"));
            log.info("Worker picked up analysis {} (record {})", analysisId, recordId);
            pipeline.run(analysisId);
        } catch (Exception ex) {
            // Pipeline already records FAILED on the analysis; log and still ACK so
            // a poison message doesn't loop forever.
            log.error("Worker error on record {}: {}", recordId, ex.getMessage(), ex);
        } finally {
            redis.opsForStream().acknowledge(
                    JobPublisher.STREAM_KEY, ConductorConfig.CONSUMER_GROUP, recordId);
        }
    }
}
