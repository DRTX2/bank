package com.drtx.bank.controller;


import com.drtx.bank.command.model.TransferMoneyCommand;
import com.drtx.bank.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfer")
public class TransferController {

    private final TransferService transferService;
    public TransferController(TransferService transferService){
        this.transferService=transferService;
    }

    @PostMapping
    public ResponseEntity<String> transfer(@RequestBody TransferMoneyCommand command) {
        transferService.transfer(command.getFromClientId(), command.getToClientId(), command.getAmount());
        return ResponseEntity.ok("Transferencia realizada con éxito");
    }

}
