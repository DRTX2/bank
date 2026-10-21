package com.drtx.bank.payments.risk;

import java.util.List;

public record RiskAssessment(int score, Decision decision, List<String> signals, String modelVersion) {

    public enum Decision { APPROVE, REVIEW, DECLINE }
}
