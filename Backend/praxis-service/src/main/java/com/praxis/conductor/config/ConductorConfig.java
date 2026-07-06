package com.praxis.conductor.config;

import com.praxis.conductor.internal.AnalysisWorker;
import com.praxis.conductor.internal.JobPublisher;
import com.praxis.conductor.internal.ProgressSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Wires the two Redis mechanisms Conductor relies on:
 *   1) a STREAM + consumer group for durable, load-balanced job delivery
 *   2) PUB/SUB for ephemeral, fan-out progress events
 * Workers run on virtual threads (Java 21) since each job is I/O-bound.
 *
 * IMPORTANT ORDERING: the consumer group MUST exist before the listener
 * container starts, otherwise the first XREADGROUP fails with
 * "NOGROUP No such key ... or consumer group ...". We therefore create the
 * group (with MKSTREAM, so it also creates the empty stream) synchronously
 * inside this bean method, right before container.start(). Doing this in a
 * separate ApplicationRunner does NOT work — runners execute after beans
 * have already started.
 */
@Configuration
public class ConductorConfig {

    public static final String CONSUMER_GROUP = "praxis-workers";
    private static final String PROGRESS_CHANNEL = "praxis:analysis:progress";

    private static final Logger log = LoggerFactory.getLogger(ConductorConfig.class);

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            AnalysisWorker worker
    ) {
        ensureConsumerGroupExists(redis);

        var options = StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(CONSUMER_GROUP, "worker-" + UUID.randomUUID()),
                StreamOffset.create(JobPublisher.STREAM_KEY, ReadOffset.lastConsumed()),
                worker);

        container.start();
        log.info("Conductor stream container started on group '{}'", CONSUMER_GROUP);
        return container;
    }

    @Bean
    public RedisMessageListenerContainer progressListenerContainer(
            RedisConnectionFactory connectionFactory,
            ProgressSubscriber subscriber
    ) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(PROGRESS_CHANNEL));
        return container;
    }

    /**
     * Idempotently create the stream + consumer group. Spring Data's createGroup
     * uses MKSTREAM, so it also creates an empty stream if the key is absent
     * (which is the normal case on a fresh Redis). If the group already exists,
     * Redis replies BUSYGROUP — we treat that as success.
     */
    private void ensureConsumerGroupExists(StringRedisTemplate redis) {
        try {
            redis.opsForStream().createGroup(JobPublisher.STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("Created Redis consumer group '{}' on stream '{}'", CONSUMER_GROUP, JobPublisher.STREAM_KEY);
        } catch (Exception ex) {
            if (isGroupAlreadyExists(ex)) {
                log.debug("Consumer group '{}' already exists — continuing", CONSUMER_GROUP);
            } else {
                throw new IllegalStateException(
                        "Could not create/verify Redis consumer group '" + CONSUMER_GROUP + "'", ex);
            }
        }
    }

    private boolean isGroupAlreadyExists(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t.getMessage() != null && t.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
