package com.drtx.bank.risk;

import java.time.Instant;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Feeds the feature store from booked transfers. Reads only the fields it needs (tolerant reader), so core-banking
 * can add fields to the event without breaking this service.
 */
@Component
class PaymentEventsConsumer {

    private final FeatureStore store;
    private final ObjectMapper json;

    PaymentEventsConsumer(FeatureStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    @KafkaListener(topics = "${risk.topic}")
    void onEvent(String payload) {
        JsonNode e = json.readTree(payload);
        if (!"TransferCompleted".equals(e.path("type").asString())) {
            return;
        }
        store.recordBooked(UUID.fromString(e.get("transferId").asString()), UUID.fromString(e.get("tenantId").asString()),
                UUID.fromString(e.get("sourceAccountId").asString()), UUID.fromString(e.get("destinationAccountId").asString()),
                e.get("amountMinor").asLong(), Instant.parse(e.get("occurredAt").asString()));
    }
}
