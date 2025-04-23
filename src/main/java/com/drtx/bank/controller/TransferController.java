package com.drtx.bank.controller;

import com.drtx.bank.service.TransferService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfer")

public class TransferController {
    private final TransferService transferService;
    public TransferController(TransferService transferService){
        this.transferService=transferService;
    }

}
