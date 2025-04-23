package com.drtx.bank.query.dto;

import lombok.Getter;
import lombok.Setter;

public class ClientDto {
    @Getter @Setter
    private Long id;

    @Getter @Setter
    private String dni;

    @Getter @Setter
    private String name;

    @Getter @Setter
    private Double balance;

    public ClientDto(Long id, String dni, String name, Double balance) {
        this.id = id;
        this.dni = dni;
        this.name = name;
        this.balance = balance;
    }
}
