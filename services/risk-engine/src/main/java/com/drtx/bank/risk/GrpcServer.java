package com.drtx.bank.risk;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Plain grpc-java server bound to the Spring lifecycle: standard health service + reflection (for grpcurl). */
@Component
class GrpcServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    private final RiskGrpcService service;
    private final RiskProperties props;
    private final HealthStatusManager health = new HealthStatusManager();
    private Server server;

    GrpcServer(RiskGrpcService service, RiskProperties props) {
        this.service = service;
        this.props = props;
    }

    @Override
    public void start() {
        try {
            server = NettyServerBuilder.forPort(props.grpcPort())
                    .executor(Executors.newVirtualThreadPerTaskExecutor())
                    .addService(service)
                    .addService(health.getHealthService())
                    .addService(ProtoReflectionServiceV1.newInstance())
                    .maxInboundMessageSize(64 * 1024)
                    .build()
                    .start();
            health.setStatus("", ServingStatus.SERVING);
            log.info("gRPC server listening on {}", server.getPort());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void stop() {
        health.enterTerminalState();
        server.shutdown();
        try {
            server.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }

    int port() {
        return server.getPort();
    }
}
