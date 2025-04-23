package com.drtx.bank.repository;

import com.drtx.bank.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Client findByDni(String dni);
}
