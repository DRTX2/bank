package com.drtx.bank.risk;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * A poison event (bad JSON, missing field) is retried briefly, then parked on {@code <topic>.dlt} with the error in
 * its headers. Without this the default handler logs and skips it: the feature store would silently miss a booking.
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandling {

    static String deadLetterTopic(String topic) {
        return topic + ".dlt";
    }

    @Bean
    NewTopic paymentsDeadLetterTopic(RiskProperties props) {
        return TopicBuilder.name(deadLetterTopic(props.topic())).partitions(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, e) -> new TopicPartition(deadLetterTopic(record.topic()), 0));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500, 2));
    }
}
