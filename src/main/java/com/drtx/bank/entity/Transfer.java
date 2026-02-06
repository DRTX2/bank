package com.drtx.bank.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="transfers")
@NoArgsConstructor
@Getter
@Setter
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne // many transfers owns to a client
    @JoinColumn(name = "from_client_id") // fk_name in database
    private Client fromClient;

    @ManyToOne
    @JoinColumn(name="to_client_id")
    private Client toClient;

    private Double amount;

    private LocalDateTime timestamp;
}
