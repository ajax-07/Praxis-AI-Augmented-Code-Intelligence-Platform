package com.praxis.conductor.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Producer side of the job queue. Appends an AnalysisJob to a Redis Stream as
 * a MAP-backed record (String field -> String value) so it round-trips
 * consistently through StringRedisTemplate and arrives at AnalysisWorker as a
 * MapRecord<String,String,String>.
 *
 * A Stream (not a list or pub/sub) is used so jobs survive a restart, are
 * processed by a consumer GROUP (add workers = more throughput, each job once),
 * and unacked jobs can be reclaimed.
 */
@Component
public class JobPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobPublisher.class);

    public static final String STREAM_KEY = "praxis:analysis:jobs";

    private final StringRedisTemplate redis;

    public JobPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publish(AnalysisJob job) {
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(Map.of(
                        "analysisId", job.analysisId().toString(),
                        "tenantId", job.tenantId().toString()))
                .withStreamKey(STREAM_KEY);
        var recordId = redis.opsForStream().add(record);
        log.info("Published job to stream {}: analysisId={} recordId={}",
                STREAM_KEY, job.analysisId(), recordId);
    }
}
