package com.drtx.bank.query.service;

import com.drtx.bank.entity.Client;
import com.drtx.bank.query.dto.ClientDto;
import com.drtx.bank.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientQueryService {
    private final ClientRepository clientRepository;

    public ClientDto getClientById(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found with ID: " + clientId));

        return mapToDto(client);
    }

    public ClientDto getClientByDni(String dni) {
        Client client = clientRepository.findByDni(dni)
                .orElseThrow(() -> new RuntimeException("Client not found with DNI: " + dni));

        return mapToDto(client);
    }

    public List<ClientDto> searchClientsByName(String name) {
        List<Client> clients = clientRepository.findByNameContainingIgnoreCase(name);
        return clients.stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<ClientDto> getAllClients() {
        List<Client> clients = clientRepository.findAll();
        return clients.stream()
                .map(this::mapToDto)
                .toList();
    }

    private ClientDto mapToDto(Client client) {
        return new ClientDto(client.getId(), client.getDni(), client.getName(), client.getBalance());
    }
}
