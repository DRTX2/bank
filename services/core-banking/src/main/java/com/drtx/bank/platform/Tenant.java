package com.drtx.bank.platform;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A financial institution (cooperative, fintech) hosted on the platform. Not tenant-scoped itself. */
@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant {

    public enum Status { ACTIVE, SUSPENDED }

    @Id
    private UUID id;
    private String code;
    private String name;
    private String country;
    private String currency;
    private String bic;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "approval_threshold_minor")
    private long approvalThresholdMinor;
    /** Annual nominal rate paid on savings, in basis points (300 = 3.00 %). */
    private int savingsRateBps = 300;
    private Instant createdAt;

    public Tenant(String code, String name, String country, String currency, String bic, long approvalThresholdMinor) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.country = country;
        this.currency = currency;
        this.bic = bic;
        this.status = Status.ACTIVE;
        this.approvalThresholdMinor = approvalThresholdMinor;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public void changeStatus(Status status) {
        this.status = status;
    }

    public void changeApprovalThreshold(long minor) {
        this.approvalThresholdMinor = minor;
    }
}
