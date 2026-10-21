package com.drtx.bank.shared.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Transactional outbox: events are stored in the same transaction as the state change, so a booked transfer
 * can never be "lost" between the database and Kafka. {@link OutboxRelay} publishes them asynchronously.
 */
@Component
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    OutboxWriter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID tenantId, String topic, String aggregateType, Object aggregateId, String eventType,
                       Object payload) {
        jdbc.update("""
                        insert into outbox_events (id, tenant_id, aggregate_type, aggregate_id, event_type, topic,
                                                   payload, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), tenantId, aggregateType, aggregateId.toString(), eventType, topic,
                json.writeValueAsString(payload), Timestamp.from(Instant.now()));
    }
}
