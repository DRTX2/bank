package com.drtx.bank.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface JournalRepository extends JpaRepository<JournalEntry, UUID> {
}

interface PostingRepository extends JpaRepository<Posting, Long> {

    @Query("""
            select new com.drtx.bank.ledger.MovementView(p.id, j.id, j.type, j.reference, j.description,
                   p.amountMinor, p.balanceAfterMinor, p.createdAt)
            from Posting p, JournalEntry j
            where p.journalId = j.id and p.accountId = :accountId and p.createdAt >= :from and p.createdAt < :to
            order by p.id desc""")
    List<MovementView> movements(UUID accountId, Instant from, Instant to);

    @Query("""
            select p.balanceAfterMinor from Posting p
            where p.accountId = :accountId and p.createdAt < :at order by p.id desc""")
    List<Long> lastBalanceBefore(UUID accountId, Instant at, Limit limit);

    default List<Long> lastBalanceBefore(UUID accountId, Instant at) {
        return lastBalanceBefore(accountId, at, Limit.of(1));
    }
}
