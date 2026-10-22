package com.drtx.bank.shared.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The producer owns its topic: created at startup if missing (brokers run with auto-create off, and MSK Serverless
 * has no other provisioning step here). Partitions bound consumer parallelism; the key is the source account.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopics {

    @Bean
    NewTopic paymentsTopic(@Value("${bank.kafka.payments-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(6).build();
    }
}
