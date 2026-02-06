package com.drtx.bank.command.handler;

import com.drtx.bank.repository.ClientRepository;
import com.drtx.bank.repository.TransferRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransferCommandHandler {
    private final ClientRepository clientRepository;
    private final TransferRepository transferRepository;

    @Transactional
    public void handle(){

    }
}
