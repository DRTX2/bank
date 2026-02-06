package com.drtx.bank.command.handler;

import com.drtx.bank.command.model.TransferMoneyCommand;
import com.drtx.bank.service.TransferService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferCommandHandler {
    private final TransferService transferService;

    @Transactional
    public void handle(TransferMoneyCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Transfer command cannot be null");
        }

        transferService.transfer(command.getFromClientId(), command.getToClientId(), command.getAmount());
    }
}
