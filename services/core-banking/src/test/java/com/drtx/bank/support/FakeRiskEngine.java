package com.drtx.bank.support;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.drtx.bank.proto.risk.v1.Decision;
import com.drtx.bank.proto.risk.v1.RiskServiceGrpc;
import com.drtx.bank.proto.risk.v1.RiskSignal;
import com.drtx.bank.proto.risk.v1.ScoreTransferRequest;
import com.drtx.bank.proto.risk.v1.ScoreTransferResponse;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/** In-process gRPC risk engine whose answer each test can script. */
public class FakeRiskEngine extends RiskServiceGrpc.RiskServiceImplBase {

    public static final Function<ScoreTransferRequest, ScoreTransferResponse> APPROVE_ALL =
            r -> response(5, Decision.DECISION_APPROVE);

    private final AtomicReference<Function<ScoreTransferRequest, ScoreTransferResponse>> behaviour =
            new AtomicReference<>(APPROVE_ALL);

    public void respond(Function<ScoreTransferRequest, ScoreTransferResponse> fn) {
        behaviour.set(fn);
    }

    public void unavailable() {
        behaviour.set(null);
    }

    public void reset() {
        behaviour.set(APPROVE_ALL);
    }

    public static ScoreTransferResponse response(int score, Decision decision, String... signals) {
        ScoreTransferResponse.Builder b = ScoreTransferResponse.newBuilder().setScore(score).setDecision(decision)
                .setModelVersion("fake");
        for (String s : signals) {
            b.addSignals(RiskSignal.newBuilder().setCode(s).setWeight(10));
        }
        return b.build();
    }

    @Override
    public void scoreTransfer(ScoreTransferRequest request, StreamObserver<ScoreTransferResponse> observer) {
        var fn = behaviour.get();
        if (fn == null) {
            observer.onError(Status.UNAVAILABLE.asRuntimeException());
            return;
        }
        observer.onNext(fn.apply(request));
        observer.onCompleted();
    }
}
