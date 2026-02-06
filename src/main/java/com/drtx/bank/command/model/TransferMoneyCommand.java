package com.drtx.bank.command.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferMoneyCommand {
    private Long fromClientId;
    private Long toClientId;
    private Double amount;
}
