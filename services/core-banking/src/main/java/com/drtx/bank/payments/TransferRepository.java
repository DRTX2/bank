package com.drtx.bank.payments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByInitiatedByAndIdempotencyKey(UUID initiatedBy, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transfer t where t.id = :id")
    Optional<Transfer> lockById(UUID id);

    @Query("""
            select coalesce(sum(t.amountMinor), 0) from Transfer t
            where t.sourceAccountId = :source and t.status = com.drtx.bank.payments.Transfer.Status.COMPLETED
              and t.createdAt >= :since""")
    long sumCompletedOutgoingSince(UUID source, Instant since);

    @Query("""
            select new com.drtx.bank.payments.TransferRow(t, s.number, d.number, s.ownerId, d.ownerId)
            from Transfer t, Account s, Account d
            where s.id = t.sourceAccountId and d.id = t.destinationAccountId
              and (:status is null or t.status = :status)
              and (:ownerId is null or s.ownerId = :ownerId or d.ownerId = :ownerId)
            order by t.createdAt desc""")
    List<TransferRow> search(Transfer.Status status, UUID ownerId, Pageable page);

    @Query("""
            select new com.drtx.bank.payments.TransferRow(t, s.number, d.number, s.ownerId, d.ownerId)
            from Transfer t, Account s, Account d
            where s.id = t.sourceAccountId and d.id = t.destinationAccountId and t.id = :id""")
    Optional<TransferRow> row(UUID id);

    List<Transfer> findByBatchIdOrderByCreatedAt(UUID batchId);

    List<Transfer> findTop50ByStatusAndUpdatedAtBefore(Transfer.Status status, Instant cutoff);

    @Query("select t.status, count(t), coalesce(sum(t.amountMinor), 0) from Transfer t where t.createdAt >= :since group by t.status")
    List<Object[]> statsSince(Instant since);
}
