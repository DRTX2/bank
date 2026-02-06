package com.drtx.bank.query.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class TransferDto {
    private Long id;
    private Long fromClientId;
    private Long toClientId;
    private Double amount;
    private LocalDateTime timestamp;
}
