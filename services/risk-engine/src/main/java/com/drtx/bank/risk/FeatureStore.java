package com.drtx.bank.risk;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeatureStore {

    private final JdbcTemplate jdbc;

    FeatureStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int attemptsSince(UUID source, Instant since) {
        return jdbc.queryForObject("select count(*) from score_log where source_account_id = ? and scored_at >= ?",
                Integer.class, source, Timestamp.from(since));
    }

    /** {count, average} of the last 30 booked transfers from the account. */
    public long[] history(UUID source) {
        return jdbc.queryForObject("""
                        select count(*), coalesce(avg(amount_minor), 0)::bigint from (
                          select amount_minor from transfer_facts where source_account_id = ?
                          order by occurred_at desc limit 30) recent""",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, source);
    }

    public boolean paidBefore(UUID source, UUID destination) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from transfer_facts where source_account_id = ? and destination_account_id = ?)""",
                Boolean.class, source, destination));
    }

    /** Distinct accounts (other than {@code excluding}) that tried to pay {@code destination} recently. */
    public int otherSendersInto(UUID destination, UUID excluding, Instant since) {
        return jdbc.queryForObject("""
                select count(distinct source_account_id) from score_log
                where destination_account_id = ? and source_account_id <> ? and scored_at >= ?""",
                Integer.class, destination, excluding, Timestamp.from(since));
    }

    public void logScore(UUID transferId, UUID tenant, UUID source, UUID destination, long amount, int score,
                         String decision, String signals, String model, Instant at) {
        jdbc.update("""
                insert into score_log (transfer_id, tenant_id, source_account_id, destination_account_id, amount_minor,
                                       score, decision, signals, model_version, scored_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (transfer_id) do nothing""",
                transferId, tenant, source, destination, amount, score, decision, signals, model, Timestamp.from(at));
    }

    /** Idempotent upsert: Kafka delivers at-least-once. */
    public void recordBooked(UUID transferId, UUID tenant, UUID source, UUID destination, long amount, Instant at) {
        jdbc.update("""
                insert into transfer_facts (transfer_id, tenant_id, source_account_id, destination_account_id,
                                            amount_minor, occurred_at)
                values (?, ?, ?, ?, ?, ?) on conflict (transfer_id) do nothing""",
                transferId, tenant, source, destination, amount, Timestamp.from(at));
    }
}
