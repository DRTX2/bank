package com.drtx.bank.risk;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Collectors;

import com.drtx.bank.proto.risk.v1.Decision;
import com.drtx.bank.proto.risk.v1.RiskServiceGrpc;
import com.drtx.bank.proto.risk.v1.RiskSignal;
import com.drtx.bank.proto.risk.v1.ScoreTransferRequest;
import com.drtx.bank.proto.risk.v1.ScoreTransferResponse;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class RiskGrpcService extends RiskServiceGrpc.RiskServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(RiskGrpcService.class);

    private final FeatureStore store;
    private final RiskProperties props;
    private final MeterRegistry meters;

    RiskGrpcService(FeatureStore store, RiskProperties props, MeterRegistry meters) {
        this.store = store;
        this.props = props;
        this.meters = meters;
    }

    @Override
    public void scoreTransfer(ScoreTransferRequest request, StreamObserver<ScoreTransferResponse> response) {
        try {
            response.onNext(score(request));
            response.onCompleted();
        } catch (IllegalArgumentException e) {
            response.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (RuntimeException e) {
            log.error("Scoring failed", e);
            response.onError(Status.INTERNAL.withDescription("scoring failed").asRuntimeException());
        }
    }

    ScoreTransferResponse score(ScoreTransferRequest r) {
        UUID transferId = UUID.fromString(r.getTransferId());
        UUID tenant = UUID.fromString(r.getTenantId());
        UUID source = UUID.fromString(r.getSourceAccountId());
        UUID destination = UUID.fromString(r.getDestinationAccountId());
        if (r.getAmountMinor() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Instant at = r.hasRequestedAt()
                ? Instant.ofEpochSecond(r.getRequestedAt().getSeconds(), r.getRequestedAt().getNanos()) : Instant.now();
        long[] history = store.history(source);
        Features features = new Features(r.getAmountMinor(),
                store.attemptsSince(source, at.minus(10, ChronoUnit.MINUTES)), history[1], (int) history[0],
                store.paidBefore(source, destination),
                store.otherSendersInto(destination, source, at.minus(30, ChronoUnit.MINUTES)) + 1,
                at.atZone(props.timezone()).toLocalTime());
        RiskRules.Score score = RiskRules.evaluate(features);
        Decision decision = score.value() >= props.declineThreshold() ? Decision.DECISION_DECLINE
                : score.value() >= props.reviewThreshold() ? Decision.DECISION_REVIEW : Decision.DECISION_APPROVE;
        String codes = score.signals().stream().map(RiskRules.Signal::code).collect(Collectors.joining(","));
        store.logScore(transferId, tenant, source, destination, r.getAmountMinor(), score.value(), decision.name(),
                codes, props.modelVersion(), at);
        meters.counter("kuntur.risk.decisions", "decision", decision.name()).increment();
        return ScoreTransferResponse.newBuilder()
                .setScore(score.value())
                .setDecision(decision)
                .setModelVersion(props.modelVersion())
                .addAllSignals(score.signals().stream().map(s -> RiskSignal.newBuilder().setCode(s.code())
                        .setWeight(s.weight()).setDescription(s.description()).build()).toList())
                .build();
    }
}
