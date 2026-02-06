package com.drtx.bank.repository;

import com.drtx.bank.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    @Query("SELECT t FROM Transfer t WHERE t.fromClient.id = :clientId")
    List<Transfer> findTransfersByFromClient(@Param("clientId") Long clientId);

    @Query("SELECT t FROM Transfer t WHERE t.toClient.id = :clientId")
    List<Transfer> findTransfersByToClient(@Param("clientId") Long clientId);
}
