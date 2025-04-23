package com.drtx.bank.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Table(name="transfers")
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne // many transfers owns to a client
    @JoinColumn(name = "from_client_id") // fk_name in database
    @Getter @Setter
    private Client fromClient;

    @ManyToOne
    @JoinColumn(name="to_client_id")
    @Getter @Setter
    private Client toClient;

    @Getter @Setter
    private Double amount;

    @Getter @Setter
    private LocalDateTime timestamp;
}
