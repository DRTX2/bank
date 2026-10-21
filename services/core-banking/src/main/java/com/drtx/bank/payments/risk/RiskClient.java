package com.drtx.bank.payments.risk;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.drtx.bank.proto.risk.v1.RiskServiceGrpc;
import com.drtx.bank.proto.risk.v1.RiskSignal;
import com.drtx.bank.proto.risk.v1.ScoreTransferRequest;
import com.drtx.bank.proto.risk.v1.ScoreTransferResponse;
import com.google.protobuf.Timestamp;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * gRPC client to the risk-engine with a hard deadline. Degradation policy when the engine is down or slow:
 * small transfers pass (fail-open below a threshold), everything else is held for human review (fail-closed).
 */
@Component
public class RiskClient {

    private static final Logger log = LoggerFactory.getLogger(RiskClient.class);
    public static final String ENGINE_UNAVAILABLE = "RISK_ENGINE_UNAVAILABLE";

    private final RiskServiceGrpc.RiskServiceBlockingStub stub;
    private final Duration deadline;
    private final long failOpenBelowMinor;
    private final Timer latency;
    private final MeterRegistry meters;

    RiskClient(ManagedChannel riskChannel, @Value("${bank.risk.deadline}") Duration deadline,
               @Value("${bank.risk.fail-open-below-minor}") long failOpenBelowMinor, MeterRegistry meters) {
        this.stub = RiskServiceGrpc.newBlockingStub(riskChannel);
        this.deadline = deadline;
        this.failOpenBelowMinor = failOpenBelowMinor;
        this.meters = meters;
        this.latency = Timer.builder("kuntur.risk.latency").publishPercentiles(0.5, 0.95, 0.99).register(meters);
    }

    public record Request(UUID tenantId, UUID transferId, UUID sourceAccountId, UUID destinationAccountId,
                          long amountMinor, String currency, UUID initiatedBy) {
    }

    public RiskAssessment score(Request r) {
        Instant now = Instant.now();
        ScoreTransferRequest request = ScoreTransferRequest.newBuilder()
                .setTenantId(r.tenantId().toString())
                .setTransferId(r.transferId().toString())
                .setSourceAccountId(r.sourceAccountId().toString())
                .setDestinationAccountId(r.destinationAccountId().toString())
                .setAmountMinor(r.amountMinor())
                .setCurrency(r.currency())
                .setInitiatedBy(r.initiatedBy().toString())
                .setRequestedAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .build();
        try {
            ScoreTransferResponse response = latency.recordCallable(() -> stub
                    .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .scoreTransfer(request));
            return new RiskAssessment(response.getScore(), map(response.getDecision()),
                    response.getSignalsList().stream().map(RiskSignal::getCode).toList(), response.getModelVersion());
        } catch (StatusRuntimeException e) {
            meters.counter("kuntur.risk.unavailable").increment();
            log.warn("Risk engine unavailable ({}), applying degradation policy", e.getStatus().getCode());
            return r.amountMinor() < failOpenBelowMinor
                    ? new RiskAssessment(0, RiskAssessment.Decision.APPROVE, List.of(ENGINE_UNAVAILABLE), "fallback")
                    : new RiskAssessment(50, RiskAssessment.Decision.REVIEW, List.of(ENGINE_UNAVAILABLE), "fallback");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RiskAssessment.Decision map(com.drtx.bank.proto.risk.v1.Decision d) {
        return switch (d) {
            case DECISION_DECLINE -> RiskAssessment.Decision.DECLINE;
            case DECISION_REVIEW -> RiskAssessment.Decision.REVIEW;
            default -> RiskAssessment.Decision.APPROVE;
        };
    }
}
