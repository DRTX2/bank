package com.drtx.bank.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.web.Amounts;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
class TransferController {

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    record CreateTransferRequest(@NotNull UUID sourceAccountId, @NotBlank @Size(max = 34) String destinationAccountNumber,
                                 @NotNull @Positive BigDecimal amount, @Size(max = 140) String remittanceInfo,
                                 @Pattern(regexp = "[A-Z]{4}") String purposeCode) {
    }

    record Decision(@Size(max = 255) String note) {
    }

    record Review(@NotNull Boolean release, @NotBlank @Size(max = 255) String note) {
    }

    public record TransferView(UUID id, String status, String sourceAccountNumber, String destinationAccountNumber,
                               BigDecimal amount, String currency, String endToEndId, UUID uetr,
                               String remittanceInfo, String purposeCode, Integer riskScore, List<String> riskSignals,
                               String failureCode, String failureReason, UUID initiatedBy, UUID reviewedBy,
                               String reviewNote, UUID batchId, Instant createdAt, Instant updatedAt) {
        static TransferView of(TransferRow row) {
            Transfer t = row.transfer();
            List<String> signals = t.getRiskSignals() == null || t.getRiskSignals().isBlank() ? List.of()
                    : List.of(t.getRiskSignals().split(","));
            return new TransferView(t.getId(), t.getStatus().name(), row.sourceNumber(), row.destinationNumber(),
                    Amounts.fromMinor(t.getAmountMinor()), t.getCurrency(), t.getEndToEndId(), t.getUetr(),
                    t.getRemittanceInfo(), t.getPurposeCode(), t.getRiskScore(), signals, t.getFailureCode(),
                    t.getFailureReason(), t.getInitiatedBy(), t.getReviewedBy(), t.getReviewNote(), t.getBatchId(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    /**
     * Idempotent: retrying with the same {@code Idempotency-Key} returns the original result instead of paying twice.
     * 201 when booked/queued, 200 on replay is indistinguishable for clients by design (same body).
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('transfers:create:own', 'transfers:create:any')")
    ResponseEntity<TransferView> create(CurrentUser user,
                                        @RequestHeader("Idempotency-Key") @Size(min = 8, max = 80) String idempotencyKey,
                                        @Valid @RequestBody CreateTransferRequest body) {
        TransferRow row = transfers.create(user, new TransferService.CreateTransfer(body.sourceAccountId(),
                body.destinationAccountNumber(), Amounts.toMinor(body.amount()), body.remittanceInfo(),
                body.purposeCode(), null, idempotencyKey, null));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransferView.of(row));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('transfers:read:own', 'transfers:read:any')")
    List<TransferView> list(CurrentUser user, @RequestParam(required = false) Transfer.Status status,
                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return transfers.search(user, status, Math.max(page, 0), Math.clamp(size, 1, 100)).stream()
                .map(TransferView::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('transfers:read:own', 'transfers:read:any')")
    TransferView get(CurrentUser user, @PathVariable UUID id) {
        return TransferView.of(transfers.readable(user, id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('transfers:approve')")
    TransferView approve(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody Decision body) {
        return TransferView.of(transfers.approve(user, id, body.note()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('transfers:approve')")
    TransferView reject(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody Decision body) {
        return TransferView.of(transfers.reject(user, id, body.note()));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('risk:review')")
    TransferView review(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody Review body) {
        return TransferView.of(transfers.review(user, id, body.release(), body.note()));
    }
}
