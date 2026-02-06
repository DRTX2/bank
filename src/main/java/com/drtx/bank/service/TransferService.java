package com.drtx.bank.service;

import com.drtx.bank.entity.Client;
import com.drtx.bank.entity.Transfer;
import com.drtx.bank.repository.ClientRepository;
import com.drtx.bank.repository.TransferRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransferService {
    private final ClientRepository clientRepository;
    private final TransferRepository transferRepository;

    private static final Double MINIMUM_AMOUNT = 0.01;

    @Transactional
    public void transfer(Long fromClientId, Long toClientId, Double amount) {
        // Validaciones iniciales
        validateTransferRequest(fromClientId, toClientId, amount);

        // Obtener clientes
        Client from = clientRepository.findById(fromClientId)
                .orElseThrow(() -> new RuntimeException("Origin client does not exist with ID: " + fromClientId));

        Client to = clientRepository.findById(toClientId)
                .orElseThrow(() -> new RuntimeException("Destination client does not exist with ID: " + toClientId));

        // Validar que no sean el mismo cliente
        if (from.getId().equals(to.getId())) {
            throw new RuntimeException("Cannot transfer to the same client");
        }

        // Validar saldo suficiente
        if (from.getBalance() < amount) {
            throw new RuntimeException("Origin client does not have enough balance. Available: " + from.getBalance() + ", Required: " + amount);
        }

        // Realizar la transferencia
        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);

        // Guardar cambios en clientes
        clientRepository.save(from);
        clientRepository.save(to);

        // Registrar la transferencia
        Transfer transfer = new Transfer();
        transfer.setFromClient(from);
        transfer.setToClient(to);
        transfer.setAmount(amount);
        transfer.setTimestamp(LocalDateTime.now());

        transferRepository.save(transfer);
    }

    private void validateTransferRequest(Long fromClientId, Long toClientId, Double amount) {
        if (fromClientId == null) {
            throw new IllegalArgumentException("Origin client ID cannot be null");
        }
        if (toClientId == null) {
            throw new IllegalArgumentException("Destination client ID cannot be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Transfer amount cannot be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than 0");
        }
        if (amount < MINIMUM_AMOUNT) {
            throw new IllegalArgumentException("Transfer amount must be at least " + MINIMUM_AMOUNT);
        }
    }
}
