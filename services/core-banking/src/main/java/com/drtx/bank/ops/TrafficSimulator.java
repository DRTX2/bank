package com.drtx.bank.ops;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.drtx.bank.accounts.Account;
import com.drtx.bank.accounts.AccountRepository;
import com.drtx.bank.payments.TransferRow;
import com.drtx.bank.payments.TransferService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;
import com.drtx.bank.shared.tenancy.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Synthetic traffic generator for demos and load tests. Mixes normal payments with fraud patterns the risk engine
 * should catch: velocity bursts, amount spikes and mule fan-in. Disabled unless {@code bank.simulator.enabled}.
 */
@Component
class TrafficSimulator {

    private static final Logger log = LoggerFactory.getLogger(TrafficSimulator.class);

    private final TransferService transfers;
    private final AccountRepository accounts;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();

    TrafficSimulator(TransferService transfers, AccountRepository accounts, PlatformTransactionManager txm,
                     @Value("${bank.simulator.enabled}") boolean enabled) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.tx = new TransactionTemplate(txm);
        this.enabled = enabled;
    }

    record Run(UUID id, int requested, double fraudRatio, Instant startedAt, AtomicInteger completed,
               AtomicInteger held, AtomicInteger rejected, AtomicInteger failed, AtomicInteger processed) {
        boolean finished() {
            return processed.get() >= requested;
        }
    }

    Run start(CurrentUser operator, int count, double fraudRatio, int delayMs) {
        if (!enabled) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Simulator disabled in this environment");
        }
        UUID tenantId = operator.tenantId();
        List<Account> pool = TenantContext.callAs(tenantId, () -> tx.execute(s -> accounts.activeCustomerAccounts()));
        if (pool.size() < 3) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Need at least 3 active customer accounts");
        }
        Run run = new Run(UUID.randomUUID(), count, fraudRatio, Instant.now(), new AtomicInteger(), new AtomicInteger(),
                new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        runs.put(run.id(), run);
        CurrentUser bot = CurrentUser.system(tenantId, EnumSet.of(Permission.TRANSFERS_CREATE_ANY,
                Permission.TRANSFERS_READ_ANY));
        Account mule = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        Thread.ofVirtual().name("simulator-" + run.id()).start(() -> TenantContext.runAs(tenantId, () -> {
            int sent = 0;
            while (sent < count) {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                boolean fraud = rnd.nextDouble() < fraudRatio;
                Account from = pool.get(rnd.nextInt(pool.size()));
                Account to = fraud && rnd.nextBoolean() ? mule : pool.get(rnd.nextInt(pool.size()));
                if (from.getId().equals(to.getId())) {
                    to = pool.get((pool.indexOf(from) + 1) % pool.size());
                }
                long amount = fraud ? rnd.nextLong(2_000_00, 9_000_00) : rnd.nextLong(5_00, 250_00);
                // A third of the fraud cases are velocity bursts from the same account.
                int burst = fraud && rnd.nextInt(3) == 0 ? Math.min(4, count - sent) : 1;
                for (int b = 0; b < burst; b++) {
                    send(run, bot, from, to, amount);
                }
                sent += burst;
                sleep(delayMs);
            }
            log.info("Simulator run {} finished", run.id());
        }));
        return run;
    }

    Run get(UUID id) {
        Run run = runs.get(id);
        if (run == null) {
            throw DomainException.notFound("Simulator run");
        }
        return run;
    }

    private void send(Run run, CurrentUser bot, Account from, Account to, long amount) {
        try {
            TransferRow row = transfers.create(bot, new TransferService.CreateTransfer(from.getId(), to.getNumber(), amount,
                    "Simulación", null, null, "sim-" + UUID.randomUUID(), null));
            switch (row.transfer().getStatus()) {
                case COMPLETED -> run.completed().incrementAndGet();
                case HELD_FOR_REVIEW, PENDING_APPROVAL -> run.held().incrementAndGet();
                case REJECTED -> run.rejected().incrementAndGet();
                default -> run.failed().incrementAndGet();
            }
        } catch (RuntimeException e) {
            run.failed().incrementAndGet();
        } finally {
            run.processed().incrementAndGet();
        }
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
