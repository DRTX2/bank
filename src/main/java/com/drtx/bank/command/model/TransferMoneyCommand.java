package com.drtx.bank.command.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TransferMoneyCommand {
    private Long fromClientId;
    private Long toClientId;
    private Double amount;
}
