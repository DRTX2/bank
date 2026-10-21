package com.drtx.bank.realtime;

import java.math.BigDecimal;
import java.util.UUID;

import com.drtx.bank.payments.PaymentEvent;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Kafka → WebSocket bridge. Each replica uses its own consumer group so every replica sees every event and can
 * push it to the sockets it holds (sockets are pinned to one replica).
 */
@Component
class PaymentEventsRelay {

    private final SimpMessagingTemplate messaging;
    private final ObjectMapper json;

    PaymentEventsRelay(SimpMessagingTemplate messaging, ObjectMapper json) {
        this.messaging = messaging;
        this.json = json;
    }

    record Notification(UUID transferId, String type, String direction, BigDecimal amount, String currency,
                        String counterparty, String status, String failureCode) {
    }

    @KafkaListener(topics = "${bank.kafka.payments-topic}", groupId = "core-ws-${random.uuid}")
    void onEvent(String payload) {
        PaymentEvent e = json.readValue(payload, PaymentEvent.class);
        BigDecimal amount = BigDecimal.valueOf(e.amountMinor(), 2);
        if (e.sourceOwnerId() != null) {
            messaging.convertAndSendToUser(e.sourceOwnerId().toString(), "/queue/notifications", new Notification(
                    e.transferId(), e.type(), "OUT", amount, e.currency(), e.destinationNumber(), e.status(), e.failureCode()));
        }
        if (e.destinationOwnerId() != null && "TransferCompleted".equals(e.type())) {
            messaging.convertAndSendToUser(e.destinationOwnerId().toString(), "/queue/notifications", new Notification(
                    e.transferId(), e.type(), "IN", amount, e.currency(), e.sourceNumber(), e.status(), null));
        }
        messaging.convertAndSend("/topic/tenant." + e.tenantId() + ".ops", e);
    }
}
