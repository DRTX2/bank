package com.drtx.bank.command.model;

import lombok.Getter;
import lombok.Setter;

public class TransferMoneyCommand {
    @Getter @Setter
    private Long fromClientId;

    @Getter @Setter
    private Long toClientId;

    @Getter @Setter
    private Double amount;

    public TransferMoneyCommand(Long fromClientId, Long toClientId, Double amount) {
        this.fromClientId = fromClientId;
        this.toClientId = toClientId;
        this.amount = amount;
    }
}
