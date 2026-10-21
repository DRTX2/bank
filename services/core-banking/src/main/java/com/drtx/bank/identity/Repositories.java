package com.drtx.bank.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByOrderByFullName();

    @Query("select count(u) > 0 from User u join u.roles r where r.id = :roleId")
    boolean existsByRole(UUID roleId);
}

interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    List<Role> findAllByOrderByName();
}

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, Instant now);
}
