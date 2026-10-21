package com.drtx.bank.risk;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("risk")
public record RiskProperties(int grpcPort, String topic, ZoneId timezone, int reviewThreshold, int declineThreshold,
                             String modelVersion) {
}
