package com.drtx.bank.shared.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.drtx.bank.shared.security.CurrentUser;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tamper-evident audit trail: each record stores SHA-256(previous hash + content), forming a per-tenant
 * hash chain. Editing or deleting any row breaks every hash after it, which {@link #verify} detects.
 * Rows are also append-only at the database level (trigger), so tampering requires DBA privileges.
 */
@Service
public class AuditService {

    static final String GENESIS = "0".repeat(64);

    private final JdbcTemplate jdbc;

    AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Joins the caller's transaction: the audit record commits or rolls back with the business change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID tenantId, CurrentUser actor, String action, String resourceType, Object resourceId,
                       String details) {
        // shortcut: serialises audit writes per tenant; at very high volume move sealing to an async chainer.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class,
                "audit:" + tenantId);
        List<String> last = jdbc.queryForList(
                "select hash from audit_log where tenant_id = ? order by id desc limit 1", String.class, tenantId);
        String prev = last.isEmpty() ? GENESIS : last.getFirst();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        AuditRecord rec = new AuditRecord(0, tenantId, actor == null ? null : actor.id(),
                actor == null ? null : actor.email(), action, resourceType,
                resourceId == null ? null : resourceId.toString(), truncate(details), now, prev, null);
        String hash = hash(rec);
        jdbc.update("""
                        insert into audit_log (tenant_id, actor_id, actor_email, action, resource_type, resource_id,
                                               details, created_at, prev_hash, hash)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                rec.tenantId(), rec.actorId(), rec.actorEmail(), rec.action(), rec.resourceType(), rec.resourceId(),
                rec.details(), Timestamp.from(now), prev, hash);
    }

    @Transactional(readOnly = true)
    public List<AuditRecord> page(UUID tenantId, String action, int page, int size) {
        String filter = action == null || action.isBlank() ? "" : " and action = ?";
        Object[] args = filter.isEmpty()
                ? new Object[]{tenantId, size, (long) page * size}
                : new Object[]{tenantId, action, size, (long) page * size};
        return jdbc.query("select * from audit_log where tenant_id = ?" + filter
                + " order by id desc limit ? offset ?", MAPPER, args);
    }

    @Transactional(readOnly = true)
    public Verification verify(UUID tenantId) {
        String expectedPrev = GENESIS;
        long checked = 0;
        // shortcut: loads the tenant's chain in one query; page by id ranges once it exceeds ~1M rows.
        for (AuditRecord rec : jdbc.query("select * from audit_log where tenant_id = ? order by id", MAPPER, tenantId)) {
            if (!rec.prevHash().equals(expectedPrev) || !rec.hash().equals(hash(rec))) {
                return new Verification(false, checked, rec.id());
            }
            expectedPrev = rec.hash();
            checked++;
        }
        return new Verification(true, checked, null);
    }

    static String hash(AuditRecord r) {
        String canonical = String.join("|", r.prevHash(), String.valueOf(r.tenantId()), String.valueOf(r.actorId()),
                String.valueOf(r.actorEmail()), r.action(), String.valueOf(r.resourceType()),
                String.valueOf(r.resourceId()), String.valueOf(r.details()),
                String.valueOf(ChronoUnit.MICROS.between(Instant.EPOCH, r.createdAt())));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }

    private static final RowMapper<AuditRecord> MAPPER = (rs, i) -> new AuditRecord(rs.getLong("id"),
            rs.getObject("tenant_id", UUID.class), rs.getObject("actor_id", UUID.class), rs.getString("actor_email"),
            rs.getString("action"), rs.getString("resource_type"), rs.getString("resource_id"),
            rs.getString("details"), rs.getTimestamp("created_at").toInstant(), rs.getString("prev_hash"),
            rs.getString("hash"));

    public record AuditRecord(long id, UUID tenantId, UUID actorId, String actorEmail, String action,
                              String resourceType, String resourceId, String details, Instant createdAt,
                              String prevHash, String hash) {
    }

    public record Verification(boolean valid, long checkedRecords, Long firstBrokenId) {
    }
}
