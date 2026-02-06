# 🏛️ Diagrama UML - Relación de Clases

## Diagrama de Clases Simplificado

```
┌─────────────────────────────────────┐
│           <<Entity>>                │
│           Client                    │
├─────────────────────────────────────┤
│ - id: Long (PK)                     │
│ - dni: String                       │
│ - name: String                      │
│ - balance: Double                   │
├─────────────────────────────────────┤
│ + getId(): Long                     │
│ + setBalance(Double): void          │
│ + getBalance(): Double              │
└──────────────────┬──────────────────┘
                   │
        ┌──────────┴──────────┐
        │ One-to-Many         │
        │ (fromClient)        │
        │ (toClient)          │
        │                     │
        ▼                     ▼
┌──────────────────────────────────────────┐
│              <<Entity>>                  │
│              Transfer                    │
├──────────────────────────────────────────┤
│ - id: Long (PK)                          │
│ - fromClient: Client (FK)                │
│ - toClient: Client (FK)                  │
│ - amount: Double                         │
│ - timestamp: LocalDateTime               │
├──────────────────────────────────────────┤
│ + getId(): Long                          │
│ + getFromClient(): Client                │
│ + getToClient(): Client                  │
│ + getAmount(): Double                    │
│ + getTimestamp(): LocalDateTime          │
└──────────────────────────────────────────┘

   Relación: Una transferencia requiere 2 clientes
   - fromClient: el que envía dinero
   - toClient: el que recibe dinero
```

---

## Diagrama de Control de Solicitud (Request Flow)

```
┌─────────────────────────────────────────┐
│       <<Controller>>                    │
│    TransferController                   │
├─────────────────────────────────────────┤
│ - transferCommandHandler: Handler       │
├─────────────────────────────────────────┤
│ + transfer(command): ResponseEntity     │
└──────────────┬──────────────────────────┘
               │ inyecta
               ▼
┌─────────────────────────────────────────┐
│        <<Component>>                    │
│   TransferCommandHandler                │
├─────────────────────────────────────────┤
│ - transferService: TransferService      │
├─────────────────────────────────────────┤
│ + handle(command): void                 │
└──────────────┬──────────────────────────┘
               │ inyecta
               ▼
┌─────────────────────────────────────────┐
│         <<Service>>                     │
│      TransferService                    │
├─────────────────────────────────────────┤
│ - clientRepository: ClientRepository    │
│ - transferRepository: TransferRepository│
├─────────────────────────────────────────┤
│ + transfer(from, to, amount): void      │
│ - validateTransferRequest(): void       │
└──────────┬──────────────────┬───────────┘
           │                  │
      inyecta                  │
      ┌────────────┐           │ inyecta
      │            │           │
      ▼            ▼           ▼
┌─────────────────────────────────────────┐
│     <<Interface>>                       │
│   JpaRepository<Client, Long>           │
│   ClientRepository                      │
├─────────────────────────────────────────┤
│ + findById(id): Optional<Client>        │
│ + findByDni(dni): Optional<Client>      │
│ + findByName(name): List<Client>        │
│ + save(client): Client                  │
└─────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│      <<Interface>>                       │
│   JpaRepository<Transfer, Long>          │
│   TransferRepository                     │
├──────────────────────────────────────────┤
│ + findById(id): Optional<Transfer>       │
│ + findTransfersByFromClient(id): List    │
│ + findTransfersByToClient(id): List      │
│ + save(transfer): Transfer               │
└──────────────────────────────────────────┘
```

---

## Diagrama de Consulta (Query Flow)

```
┌──────────────────────────────┐
│    <<Controller>>            │
│  TransferQueryController     │
├──────────────────────────────┤
│ - queryService: QueryService │
├──────────────────────────────┤
│ + getTransfer(): ResponseEnt │
│ + getTransfersFrom(): Resp.  │
│ + getTransfersTo(): Resp.    │
└──────────────┬───────────────┘
               │ inyecta
               ▼
┌──────────────────────────────────────┐
│        <<Service>>                   │
│    TransferQueryService              │
├──────────────────────────────────────┤
│ - transferRepository: Repository     │
├──────────────────────────────────────┤
│ + getTransferById(id): TransferDto   │
│ + getByFromClient(id): List<Dto>    │
│ + getByToClient(id): List<Dto>      │
│ - mapToDto(entity): TransferDto      │
└──────────────┬──────────────────────┘
               │ inyecta
               ▼
┌──────────────────────────────────────┐
│     <<Interface>>                    │
│  TransferRepository                  │
├──────────────────────────────────────┤
│ + findById(id): Optional<Transfer>   │
│ + findAll(): List<Transfer>          │
│ + findByFromClient(id): List         │
│ + findByToClient(id): List           │
└──────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────┐
│      <<DTO>>                         │
│    TransferDto                       │
├──────────────────────────────────────┤
│ - id: Long                           │
│ - fromClientId: Long                 │
│ - toClientId: Long                   │
│ - amount: Double                     │
│ - timestamp: LocalDateTime           │
└──────────────────────────────────────┘
```

---

## Diagrama Completo de Arquitectura

```
                        HTTP CLIENT (Browser/Postman)
                               │
            ┌──────────────────┼──────────────────┐
            │                  │                  │
            │                  │                  │
    POST /transfer      GET /transfers/*    GET /clients/*
            │                  │                  │
            ▼                  ▼                  ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
    │TransferCtr   │   │TransferQryCtr│   │ClientCtr     │
    └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
           │                  │                  │
           ├──────────────────┴──────────────────┤
           │                                      │
           ▼                                      ▼
    ┌─────────────────┐          ┌────────────────────────┐
    │ COMMAND         │          │     QUERY              │
    │ (ESCRIBIR)      │          │     (LEER)             │
    ├─────────────────┤          ├────────────────────────┤
    │ Handler         │          │ TransferQueryService   │
    │  - handle()     │          │  - getTransferById()   │
    │    │            │          │  - getByFromClient()   │
    │    └────────────┼──────────┤  - getByToClient()     │
    │         │       │          │  - getAllTransfers()   │
    │         ▼       │          └──────┬─────────────────┘
    │   TransferSvc   │                 │
    │    - validate   │                 │ ClientQueryService
    │    - transfer   │                 │  - getClientById()
    │    - update     │                 │  - getByDni()
    └────┬────────┬───┘                 │  - searchByName()
         │        │                     │  - getAllClients()
         ▼        ▼                     │
    ┌────────────────────────┐          └────┬──────────┐
    │  REPOSITORIES          │               │          │
    │  (DATA ACCESS)         │               ▼          ▼
    │                        │        ┌──────────────────────┐
    │ ClientRepository       │        │   DTOs               │
    │ TransferRepository     │        │ ┌──────────────────┐ │
    └────────┬───────────────┘        │ │ TransferDto      │ │
             │                         │ │ ClientDto        │ │
             │                         │ └──────────────────┘ │
             ▼                         └──────────────────────┘
    ┌──────────────────────────────────────┐
    │        DATABASE (H2)                 │
    │                                      │
    │ ┌──────────────┐  ┌──────────────┐  │
    │ │ clients      │  │ transfers    │  │
    │ │ - id         │  │ - id         │  │
    │ │ - dni        │  │ - from_id (FK)
    │ │ - name       │  │ - to_id (FK) │  │
    │ │ - balance    │  │ - amount     │  │
    │ │              │  │ - timestamp  │  │
    │ └──────────────┘  └──────────────┘  │
    └──────────────────────────────────────┘
```

---

## Diagrama de Patrones de Diseño

```
┌────────────────────────────────────────────────────────────┐
│         CQRS Pattern (Command Query Responsibility)        │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  COMMAND (Escritura/Modificación)                         │
│  ┌──────────────────────────────────────────────────┐     │
│  │                                                   │     │
│  │  Client HTTP                                     │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  TransferController                             │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  TransferCommandHandler                         │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  TransferService                                │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  ClientRepository.save()                        │     │
│  │  TransferRepository.save()                      │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  DATABASE UPDATE/INSERT                         │     │
│  │                                                   │     │
│  └──────────────────────────────────────────────────┘     │
│                                                            │
│  QUERY (Lectura/Consulta)                                │
│  ┌──────────────────────────────────────────────────┐     │
│  │                                                   │     │
│  │  Client HTTP                                     │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  TransferQueryController                        │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  TransferQueryService                           │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  TransferRepository.findBy*()                   │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  DATABASE SELECT                                │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  Entity → DTO                                   │     │
│  │    │                                             │     │
│  │    ▼                                             │     │
│  │  JSON Response                                  │     │
│  │                                                   │     │
│  └──────────────────────────────────────────────────┘     │
│                                                            │
│  VENTAJA:                                                 │
│  - Lectura y escritura completamente separadas           │
│  - Cada una optimizada para su propósito                 │
│  - Fácil de escalar independientemente                   │
│  - Facilita testing (mock diferentes caminos)            │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## Diagrama de Inyección de Dependencias (DI)

```
┌─────────────────────────────────────────────────────────────┐
│   Spring IoC Container (Contenedor de Inversión Control)   │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ BEANS CREADOS Y MANTENIDOS POR SPRING              │ │
│  │                                                       │ │
│  │ transferController ──┐                              │ │
│  │                      ├──> Inyecta                   │ │
│  │                      ▼                              │ │
│  │              transferCommandHandler                 │ │
│  │                      │                              │ │
│  │                      ├──> Inyecta                   │ │
│  │                      ▼                              │ │
│  │               transferService                       │ │
│  │                      │                              │ │
│  │        ┌─────────────┼─────────────┐               │ │
│  │        ├─> Inyecta   │             │ Inyecta       │ │
│  │        ▼             ▼             ▼               │ │
│  │   ClientRepository   TransferRepository            │ │
│  │                                                       │ │
│  │ transferQueryController ──┐                         │ │
│  │                           ├──> Inyecta              │ │
│  │                           ▼                         │ │
│  │                   transferQueryService              │ │
│  │                           │                         │ │
│  │                           └──> Inyecta              │ │
│  │                               TransferRepository    │ │
│  │                                                       │ │
│  │ clientController ──┐                                │ │
│  │                    ├──> Inyecta                     │ │
│  │                    ▼                                │ │
│  │            clientQueryService                       │ │
│  │                    │                                │ │
│  │                    └──> Inyecta                     │ │
│  │                        ClientRepository            │ │
│  │                                                       │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  BENEFICIOS:                                               │
│  ✓ Bajo acoplamiento (las clases no crean sus propios deps)
│  ✓ Fácil de testear (se pueden inyectar mocks)           │
│  ✓ Centralizado (un solo lugar gestiona todas las deps)  │
│  ✓ Lazy loading (los beans se crean cuando se necesitan) │
│  ✓ Singleton pattern (una instancia para toda la app)    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Tabla de Relaciones entre Clases

| De | A | Tipo | Via | Descripción |
|----|----|------|-----|-------------|
| TransferController | TransferCommandHandler | Inyección | Constructor | El controller usa el handler |
| TransferCommandHandler | TransferService | Inyección | Constructor | El handler delega al servicio |
| TransferService | ClientRepository | Inyección | Constructor | El servicio accede a clientes |
| TransferService | TransferRepository | Inyección | Constructor | El servicio guarda transferencias |
| Transfer | Client | JPA FK | @ManyToOne | Una transferencia tiene 2 clientes |
| Client | Transfer | JPA | Relación inversa | Un cliente participa en varias transferencias |
| TransferQueryController | TransferQueryService | Inyección | Constructor | Controller consulta mediante service |
| TransferQueryService | TransferRepository | Inyección | Constructor | Query service consulta BD |
| ClientController | ClientQueryService | Inyección | Constructor | Controller consulta clientes |
| ClientQueryService | ClientRepository | Inyección | Constructor | Query service consulta clientes |
| TransferQueryService | TransferDto | Conversion | mapToDto() | Service convierte Entity a DTO |
| ClientQueryService | ClientDto | Conversion | mapToDto() | Service convierte Entity a DTO |

---

## Cycle de Vida de una Petición HTTP (POST /transfer)

```
TIMELINE:

1. [HTTP REQUEST ARRIVES]
   POST /transfer
   Content-Type: application/json
   {
     "fromClientId": 1,
     "toClientId": 2,
     "amount": 100.0
   }

2. [SPRING DISPATCHES REQUEST]
   DispatcherServlet recibe la petición
   Mapea a TransferController::transfer()

3. [DESERIALIZE INPUT]
   JSON → TransferMoneyCommand object
   {
     fromClientId: 1
     toClientId: 2
     amount: 100.0
   }

4. [INJECT DEPENDENCY]
   Spring inyecta TransferCommandHandler
   (ya pre-creado en startup)

5. [CALL CONTROLLER METHOD]
   transferController.transfer(command)
   
6. [INJECT HANDLER DEPENDENCY]
   Spring inyecta TransferService en Handler
   
7. [CALL HANDLER]
   transferCommandHandler.handle(command)

8. [HANDLER DELEGATES TO SERVICE]
   Extrae parámetros del comando
   Llama a transferService.transfer()

9. [SERVICE VALIDATION]
   Ejecuta validateTransferRequest()
   Valida parámetros, montos, etc.

10. [FETCH ENTITIES FROM DB]
    clientRepository.findById(1) → Client #1
    clientRepository.findById(2) → Client #2
    
    SQL: SELECT * FROM clients WHERE id = 1
    SQL: SELECT * FROM clients WHERE id = 2

11. [BUSINESS LOGIC]
    Valida saldo suficiente
    Cliente 1: 500 >= 100? ✓
    Cliente 2 no puede ser igual a Cliente 1? ✓

12. [MODIFY ENTITIES]
    client1.setBalance(400)
    client2.setBalance(400)

14. [PERSIST CHANGES]
    clientRepository.save(client1)
    SQL: UPDATE clients SET balance = 400 WHERE id = 1
    
    clientRepository.save(client2)
    SQL: UPDATE clients SET balance = 400 WHERE id = 2

15. [CREATE AND SAVE TRANSFER]
    transfer = new Transfer()
    transfer.setFromClient(client1)
    transfer.setToClient(client2)
    transfer.setAmount(100.0)
    transfer.setTimestamp(now)
    
    transferRepository.save(transfer)
    SQL: INSERT INTO transfers VALUES(1, 2, 100.0, ...)

16. [TRANSACTION COMMIT]
    @Transactional finaliza
    Todos los cambios se commiten a la BD
    (Si hay error en algún paso, todo se rollback)

17. [RETURN RESPONSE]
    return ResponseEntity.ok("Transferencia realizada con éxito")

18. [SERIALIZE RESPONSE]
    ResponseEntity → JSON
    {
      "statusCode": 200,
      "body": "Transferencia realizada con éxito"
    }

19. [SEND HTTP RESPONSE]
    HTTP/1.1 200 OK
    Content-Type: application/json
    "Transferencia realizada con éxito"

20. [REQUEST COMPLETE]
    Cliente recibe respuesta
```

---

*Diagramas UML para visualizar mejor la arquitectura y relaciones de clases* 📐
