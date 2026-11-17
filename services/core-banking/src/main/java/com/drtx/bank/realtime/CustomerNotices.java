package com.drtx.bank.realtime;

import java.util.UUID;

import com.drtx.bank.shared.outbox.OutboxWriter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-facing events that are not payments (loans, interest). They go through the outbox like every domain
 * event, on their own topic so the risk engine's payment consumer never sees them.
 */
@Component
public class CustomerNotices {

    public enum Type { LoanDisbursed, InstallmentCollected, InstallmentMissed, InterestCredited, LoanPaidOff }

    public record CustomerNotice(UUID id, UUID customerId, Type type, long amountMinor, String currency,
                                 Integer installment, Integer installments) {
    }

    private final OutboxWriter outbox;
    private final String topic;

    CustomerNotices(OutboxWriter outbox, @Value("${bank.kafka.notifications-topic}") String topic) {
        this.outbox = outbox;
        this.topic = topic;
    }

    /** Joins the caller's transaction: the customer hears about it only if the change commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void send(UUID tenantId, UUID customerId, Type type, long amountMinor, String currency,
                     Integer installment, Integer installments) {
        outbox.append(tenantId, topic, "customer", customerId, type.name(), new CustomerNotice(UUID.randomUUID(),
                customerId, type, amountMinor, currency, installment, installments));
    }
}
