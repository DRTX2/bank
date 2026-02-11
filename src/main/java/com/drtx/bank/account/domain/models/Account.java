package com.drtx.bank.account.domain.models;

import com.drtx.bank.client.domain.models.Client;

import java.util.UUID;

public class Account {
    private UUID id;
    private String accountNumber;
    private Client owner;
}
