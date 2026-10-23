package com.drtx.bank.accounts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Locks rows in primary-key order so concurrent transfers A->B and B->A cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAllOrdered(Collection<UUID> ids);

    Optional<Account> findByNumber(String number);

    List<Account> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    List<Account> findByTypeNotOrderByCreatedAtDesc(Account.Type type);

    Optional<Account> findByTypeAndAlias(Account.Type type, String alias);

    /** Savings accounts that earn interest at the day close (frozen ones too: freezing only blocks debits). */
    @Query("""
            select a from Account a where a.type = com.drtx.bank.accounts.Account.Type.SAVINGS
              and a.status <> com.drtx.bank.accounts.Account.Status.CLOSED and a.balanceMinor > 0""")
    List<Account> interestBearing();

    @Query("""
            select new com.drtx.bank.accounts.AccountTotals(count(a), coalesce(sum(a.balanceMinor), 0))
            from Account a where a.type <> com.drtx.bank.accounts.Account.Type.INTERNAL""")
    AccountTotals customerTotals();

    @Query("""
            select a from Account a where a.type <> com.drtx.bank.accounts.Account.Type.INTERNAL
              and a.status = com.drtx.bank.accounts.Account.Status.ACTIVE""")
    List<Account> activeCustomerAccounts();
}
