package com.drtx.bank.client.domain.models;

import com.drtx.bank.account.domain.models.Account;

import java.util.List;
import java.util.UUID;

public class Client {
    private UUID id;
    private String dni;
    private String name;
    private List<Account> accounts;
}
