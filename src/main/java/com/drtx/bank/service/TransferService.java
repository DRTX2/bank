package com.drtx.bank.service;

import com.drtx.bank.repository.ClientRepository;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@NoArgsConstructor
public class TransferService {

    private ClientRepository clientRepository;
    
}
