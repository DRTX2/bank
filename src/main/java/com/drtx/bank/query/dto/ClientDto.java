package com.drtx.bank.query.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ClientDto {
    private Long id;
    private String dni;
    private String name;
    private Double balance;
}
