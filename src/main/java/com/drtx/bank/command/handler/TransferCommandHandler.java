package com.drtx.bank.command.handler;

import com.drtx.bank.repository.ClientRepository;
import com.drtx.bank.repository.TransferRepository;
import jakarta.transaction.Transactional;

public class TransferCommandHandler {
    private final ClientRepository clientRepository;
    private final TransferRepository transferRepository;
    public TransferCommandHandler(ClientRepository clientRepository, TransferRepository transferRepository){
        this.clientRepository=clientRepository;
        this.transferRepository=transferRepository;
    }
    @Transactional
    public void handle(){

    }
}
