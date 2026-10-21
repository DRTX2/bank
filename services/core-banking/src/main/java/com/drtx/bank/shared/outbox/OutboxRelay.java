package com.drtx.bank.shared.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes pending outbox rows to Kafka. {@code FOR UPDATE SKIP LOCKED} lets several replicas relay in
 * parallel without double-sending; delivery is at-least-once, consumers are idempotent by event id.
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final Counter published;

    OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tx = tx;
        this.published = meters.counter("kuntur.outbox.published");
    }

    @Scheduled(fixedDelayString = "${bank.outbox.poll-interval}")
    void relay() {
        Integer sent;
        do {
            sent = tx.execute(status -> publishBatch());
        } while (sent != null && sent == 100);
    }

    private int publishBatch() {
        List<Row> rows = jdbc.query("""
                        select id, topic, aggregate_id, payload from outbox_events
                        where published_at is null order by created_at limit 100 for update skip locked""",
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                        rs.getString("aggregate_id"), rs.getString("payload")));
        for (Row row : rows) {
            try {
                kafka.send(row.topic(), row.key(), row.payload()).get(5, TimeUnit.SECONDS);
                jdbc.update("update outbox_events set published_at = ?, attempts = attempts + 1 where id = ?",
                        Timestamp.from(Instant.now()), row.id());
                published.increment();
            } catch (Exception e) {
                // Keep ordering: stop at the first failure and retry the rest on the next tick.
                log.warn("Outbox publish failed for {}: {}", row.id(), e.getMessage());
                jdbc.update("update outbox_events set attempts = attempts + 1 where id = ?", row.id());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return 0;
            }
        }
        return rows.size();
    }

    private record Row(UUID id, String topic, String key, String payload) {
    }
}
