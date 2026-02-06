package com.drtx.bank.controller;

import com.drtx.bank.query.dto.ClientDto;
import com.drtx.bank.query.service.ClientQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/clients")
public class ClientController {
    private final ClientQueryService clientQueryService;

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientDto> getClient(@PathVariable Long clientId) {
        ClientDto client = clientQueryService.getClientById(clientId);
        return ResponseEntity.ok(client);
    }

    @GetMapping("/dni/{dni}")
    public ResponseEntity<ClientDto> getClientByDni(@PathVariable String dni) {
        ClientDto client = clientQueryService.getClientByDni(dni);
        return ResponseEntity.ok(client);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ClientDto>> searchByName(@RequestParam String name) {
        List<ClientDto> clients = clientQueryService.searchClientsByName(name);
        return ResponseEntity.ok(clients);
    }

    @GetMapping
    public ResponseEntity<List<ClientDto>> getAllClients() {
        List<ClientDto> clients = clientQueryService.getAllClients();
        return ResponseEntity.ok(clients);
    }
}

