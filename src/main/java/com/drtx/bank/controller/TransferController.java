package com.drtx.bank.controller;

import com.drtx.bank.command.handler.TransferCommandHandler;
import com.drtx.bank.command.model.TransferMoneyCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/transfer")
public class TransferController {

    private final TransferCommandHandler transferCommandHandler;

    @PostMapping
    public ResponseEntity<String> transfer(@RequestBody TransferMoneyCommand command) {
        transferCommandHandler.handle(command);
        return ResponseEntity.ok("Transferencia realizada con éxito");
    }

}
