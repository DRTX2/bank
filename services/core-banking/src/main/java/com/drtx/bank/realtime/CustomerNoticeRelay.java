package com.drtx.bank.realtime;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/** Kafka → the customer's own WebSocket queue, like {@link PaymentEventsRelay} (one consumer group per replica). */
@Component
class CustomerNoticeRelay {

    private final SimpMessagingTemplate messaging;
    private final ObjectMapper json;

    CustomerNoticeRelay(SimpMessagingTemplate messaging, ObjectMapper json) {
        this.messaging = messaging;
        this.json = json;
    }

    record Notice(UUID id, CustomerNotices.Type type, BigDecimal amount, String currency, Integer installment,
                  Integer installments) {
    }

    @KafkaListener(topics = "${bank.kafka.notifications-topic}", groupId = "core-notices-${random.uuid}")
    void onNotice(String payload) {
        CustomerNotices.CustomerNotice n = json.readValue(payload, CustomerNotices.CustomerNotice.class);
        messaging.convertAndSendToUser(n.customerId().toString(), "/queue/notifications", new Notice(n.id(), n.type(),
                BigDecimal.valueOf(n.amountMinor(), 2), n.currency(), n.installment(), n.installments()));
    }
}
