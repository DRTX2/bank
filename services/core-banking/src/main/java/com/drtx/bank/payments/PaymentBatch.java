package com.drtx.bank.payments;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One uploaded pain.001 message. MsgId is unique per institution, so re-sending a file is rejected (DUPL). */
@Entity
@Table(name = "payment_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentBatch extends TenantOwned {

    @Id
    private UUID id;
    private String messageId;
    private UUID createdBy;
    private int numberOfTxs;
    private long controlSumMinor;
    private String status;
    private Instant createdAt;

    PaymentBatch(UUID tenantId, String messageId, UUID createdBy, int numberOfTxs, long controlSumMinor) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.messageId = messageId;
        this.createdBy = createdBy;
        this.numberOfTxs = numberOfTxs;
        this.controlSumMinor = controlSumMinor;
        this.status = "RECEIVED";
        this.createdAt = Instant.now();
    }

    void finish(String groupStatus) {
        this.status = groupStatus;
    }
}

interface PaymentBatchRepository extends JpaRepository<PaymentBatch, UUID> {

    boolean existsByMessageId(String messageId);

    java.util.List<PaymentBatch> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    java.util.List<PaymentBatch> findAllByOrderByCreatedAtDesc();
}
