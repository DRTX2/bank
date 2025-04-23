package com.drtx.bank.query.service;

import com.drtx.bank.entity.Client;
import com.drtx.bank.query.dto.ClientDto;
import com.drtx.bank.repository.ClientRepository;
import org.springframework.stereotype.Service;

@Service
public class ClientQueryService {
    private final ClientRepository clientRepository;

    public ClientQueryService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public ClientDto getClientById(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return new ClientDto(client.getId(), client.getDni(), client.getName(), client.getBalance());
    }
}
