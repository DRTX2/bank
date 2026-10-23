package com.drtx.bank.ops;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One closed business day of an institution: who closed it and the interest it credited. */
@Entity
@Table(name = "day_closes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DayClose extends TenantOwned {

    @Id
    private UUID id;
    private LocalDate businessDate;
    private String closedByEmail;
    private int accountsCredited;
    private long interestMinor;
    private int rateBps;
    private UUID journalId;
    private Instant closedAt;

    DayClose(UUID tenantId, LocalDate businessDate, String closedByEmail, int accountsCredited, long interestMinor,
             int rateBps, UUID journalId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.businessDate = businessDate;
        this.closedByEmail = closedByEmail;
        this.accountsCredited = accountsCredited;
        this.interestMinor = interestMinor;
        this.rateBps = rateBps;
        this.journalId = journalId;
        this.closedAt = Instant.now();
    }
}

interface DayCloseRepository extends JpaRepository<DayClose, UUID> {

    boolean existsByBusinessDate(LocalDate businessDate);

    List<DayClose> findTop30ByOrderByBusinessDateDesc();
}
