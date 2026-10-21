package com.drtx.bank.identity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends TenantOwned {

    @Id
    private UUID id;
    private String name;
    private String description;
    private boolean system;
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    private Set<String> permissions = new HashSet<>();

    public Role(UUID tenantId, String name, String description, boolean system, Set<String> permissions) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.system = system;
        this.permissions = new HashSet<>(permissions);
        this.createdAt = Instant.now();
    }

    void update(String description, Set<String> permissions) {
        this.description = description;
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }
}
