package com.drtx.bank.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.drtx.bank.proto.risk.v1.Decision;
import com.drtx.bank.proto.risk.v1.RiskServiceGrpc;
import com.drtx.bank.proto.risk.v1.RiskSignal;
import com.drtx.bank.proto.risk.v1.ScoreTransferRequest;
import com.drtx.bank.proto.risk.v1.ScoreTransferResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"risk.grpc-port=0", "server.port=0",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"})
@Import(RiskEngineIntegrationTest.Containers.class)
class RiskEngineIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
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
    }

    @Autowired
    GrpcServer server;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    ConsumerFactory<String, String> consumers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    private ManagedChannel channel;
    private RiskServiceGrpc.RiskServiceBlockingStub stub;
    // Fixed clock: ODD_HOURS must not depend on when the build runs.
    private static final Instant NOON_IN_QUITO = Instant.parse("2026-10-21T17:00:00Z");
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void connect() {
        channel = ManagedChannelBuilder.forAddress("localhost", server.port()).usePlaintext().build();
        stub = RiskServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void close() {
        channel.shutdownNow();
    }

    @Test
    void velocityBurstEscalatesFromApproveToReview() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        ScoreTransferResponse first = stub.scoreTransfer(request(source, destination, 150_00));
        assertThat(first.getDecision()).isEqualTo(Decision.DECISION_APPROVE);
        assertThat(first.getScore()).isEqualTo(10); // NEW_BENEFICIARY only

        ScoreTransferResponse last = first;
        for (int i = 0; i < 5; i++) {
            last = stub.scoreTransfer(request(source, destination, 150_00));
        }
        // 6th attempt in 10 minutes: VELOCITY_10M (30) + NEW_BENEFICIARY (10) reaches the review threshold
        assertThat(last.getSignalsList()).extracting(RiskSignal::getCode).contains("VELOCITY_10M");
        assertThat(last.getScore()).isEqualTo(40);
        assertThat(last.getDecision()).isEqualTo(Decision.DECISION_REVIEW);
    }

    @Test
    void largeSpikeToMuleAccountIsDeclined() throws Exception {
        UUID mule = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            publishCompleted(victim, UUID.randomUUID(), 20_00);
        }
        awaitFacts(victim, 3);
        for (int i = 0; i < 4; i++) {
            stub.scoreTransfer(request(UUID.randomUUID(), mule, 50_00));
        }
        ScoreTransferResponse r = stub.scoreTransfer(request(victim, mule, 6_000_00));
        assertThat(r.getSignalsList()).extracting(RiskSignal::getCode)
                .contains("LARGE_AMOUNT", "AMOUNT_SPIKE", "NEW_BENEFICIARY", "MULE_FAN_IN");
        assertThat(r.getDecision()).isEqualTo(Decision.DECISION_DECLINE);
        assertThat(r.getModelVersion()).isNotBlank();
    }

    @Test
    void kafkaEventsAreConsumedIdempotently() throws Exception {
        UUID source = UUID.randomUUID();
        UUID transferId = publishCompleted(source, UUID.randomUUID(), 10_00);
        publish(transferId, source, UUID.randomUUID(), 10_00); // redelivery
        awaitFacts(source, 1);
        Thread.sleep(500);
        assertThat(jdbc.queryForObject("select count(*) from transfer_facts where source_account_id = ?", Integer.class,
                source)).isEqualTo(1);
    }

    @Test
    void poisonEventIsParkedOnTheDeadLetterTopic() throws Exception {
        String poison = "{\"type\":\"TransferCompleted\",\"transferId\":\"not-a-uuid-" + UUID.randomUUID() + "\"}";
        kafka.send("bank.payments.events.v1", "k", poison).get();

        try (var consumer = consumers.createConsumer("dlt-test-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of("bank.payments.events.v1.dlt"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (poison.equals(record.value())) {
                        assertThat(record.headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
                        return;
                    }
                }
            }
        }
        throw new AssertionError("Poison event never reached the dead-letter topic");
    }

    private ScoreTransferRequest request(UUID source, UUID destination, long amount) {
        return ScoreTransferRequest.newBuilder().setTenantId(tenant.toString()).setTransferId(UUID.randomUUID().toString())
                .setSourceAccountId(source.toString()).setDestinationAccountId(destination.toString())
                .setAmountMinor(amount).setCurrency("USD").setInitiatedBy(UUID.randomUUID().toString())
                .setRequestedAt(com.google.protobuf.Timestamp.newBuilder().setSeconds(NOON_IN_QUITO.getEpochSecond()))
                .build();
    }

    private UUID publishCompleted(UUID source, UUID destination, long amount) throws Exception {
        UUID id = UUID.randomUUID();
        publish(id, source, destination, amount);
        return id;
    }

    private void publish(UUID id, UUID source, UUID destination, long amount) throws Exception {
        String payload = json.writeValueAsString(Map.of("type", "TransferCompleted", "transferId", id,
                "tenantId", tenant, "sourceAccountId", source, "destinationAccountId", destination,
                "amountMinor", amount, "occurredAt", Instant.now().toString(), "newFieldFromTheFuture", "ignored"));
        kafka.send("bank.payments.events.v1", source.toString(), payload).get();
    }

    private void awaitFacts(UUID source, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            Integer n = jdbc.queryForObject("select count(*) from transfer_facts where source_account_id = ?",
                    Integer.class, source);
            if (n != null && n >= expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Kafka events were not consumed in time");
    }
}
