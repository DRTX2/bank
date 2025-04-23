package com.drtx.bank.service;

import com.drtx.bank.entity.Client;
import com.drtx.bank.entity.Transfer;
import com.drtx.bank.repository.ClientRepository;
import com.drtx.bank.repository.TransferRepository;
import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TransferService {
    private final ClientRepository clientRepository;
    private final TransferRepository transferRepository;

    public TransferService(ClientRepository clientRepository, TransferRepository transferRepository) {
        this.clientRepository = clientRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional
    public void transfer(Long fromClientId, Long toClientId, Double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        Client from = clientRepository.findById(fromClientId)
                .orElseThrow(() -> new RuntimeException("Origin client does not exists with ID "+fromClientId));

        Client to = clientRepository.findById(toClientId)
                .orElseThrow(() -> new RuntimeException("Arrive client does not exists with ID "+fromClientId));

        if (from.getBalance() < amount) {
            throw new RuntimeException("");
        }

        // Actualizamos los saldos
        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);

        // Guardamos los cambios en los clientes
        clientRepository.save(from);
        clientRepository.save(to);

        // Registramos la transferencia
        Transfer transfer = new Transfer();
        transfer.setFromClient(from);
        transfer.setToClient(to);
        transfer.setAmount(amount);
        transfer.setTimestamp(LocalDateTime.now());

        transferRepository.save(transfer);
    }
}
