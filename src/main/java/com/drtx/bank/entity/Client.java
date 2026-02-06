package com.drtx.bank.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name="clients")
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String dni;
    private String name;
    private Double balance;

    @OneToMany(mappedBy = "fromClient")
    private List<Transfer> originTransfers;

    @OneToMany(mappedBy = "toClient")
    private List<Transfer> incomingTransfers;
}
