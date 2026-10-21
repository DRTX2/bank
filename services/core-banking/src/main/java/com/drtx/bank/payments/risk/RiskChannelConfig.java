package com.drtx.bank.payments.risk;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RiskChannelConfig {

    // Plaintext inside the private network; TLS terminates at the service mesh (ECS Service Connect) in AWS.
    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel riskChannel(@Value("${bank.risk.target}") String target) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        // Connect eagerly so the first payment does not pay the TCP + HTTP/2 handshake inside its deadline.
        channel.getState(true);
        return channel;
    }
}
