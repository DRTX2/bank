package com.drtx.bank.support;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.1.0");
    }

    @Bean
    FakeRiskEngine fakeRiskEngine() {
        return new FakeRiskEngine();
    }

    @Bean(destroyMethod = "shutdownNow")
    Server fakeRiskServer(FakeRiskEngine engine) {
        try {
            return InProcessServerBuilder.forName("risk-test").directExecutor().addService(engine).build().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Bean(destroyMethod = "shutdownNow")
    @Primary
    ManagedChannel testRiskChannel(Server fakeRiskServer) {
        return InProcessChannelBuilder.forName("risk-test").directExecutor().build();
    }
}
