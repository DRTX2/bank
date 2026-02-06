# 🔗 Dependencias y Relaciones entre Clases

## Mapa de Inyecciones de Dependencias

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        INYECCIONES DE DEPENDENCIAS                       │
│                     (Lo que cada clase necesita)                         │
└─────────────────────────────────────────────────────────────────────────┘

TransferController
├── Inyecta: TransferCommandHandler
└── Usa: para procesar POST /transfer

TransferCommandHandler
├── Inyecta: TransferService
└── Usa: para ejecutar la lógica de negocio

TransferService
├── Inyecta: ClientRepository
├── Inyecta: TransferRepository
└── Usa: para acceder a los datos en BD

TransferQueryController
├── Inyecta: TransferQueryService
└── Usa: para procesar GET /transfers/*

TransferQueryService
├── Inyecta: TransferRepository
└── Usa: para consultar transferencias

ClientController
├── Inyecta: ClientQueryService
└── Usa: para procesar GET /clients/*

ClientQueryService
├── Inyecta: ClientRepository
└── Usa: para consultar clientes

Repositorios (No inyectan nada)
├── ClientRepository: hereda de JpaRepository<Client, Long>
└── TransferRepository: hereda de JpaRepository<Transfer, Long>
```

---

## Árbol de Llamadas (Call Stack)

### Ejemplo: POST /transfer (Crear Transferencia)

```
┌─────────────────────────────────────┐
│   Cliente HTTP                      │
│   POST /transfer                    │
│   Body: {from:1, to:2, amount:100}  │
└────────────────┬────────────────────┘
                 │
                 ▼
         ┌───────────────────────────┐
         │ TransferController        │
         │ transfer(@RequestBody cmd)│
         └────────────┬──────────────┘
                      │
                      ▼ (llama)
         ┌───────────────────────────────────────┐
         │ TransferCommandHandler                │
         │ handle(TransferMoneyCommand command)  │
         └────────────┬──────────────────────────┘
                      │
          ┌───────────┘
          │
          ▼ (llama)
    ┌────────────────────────────┐
    │ TransferService            │
    │ transfer(from, to, amount) │
    └────────┬───────────────────┘
             │
    ┌────────┴─────────┐
    │                  │
    ▼                  ▼
┌──────────────┐  ┌─────────────────┐
│ ClientRepo   │  │ TransferRepo    │
│ .findById(1) │  │ .save(transfer) │
│ .findById(2) │  │                 │
│ .save(client)│  │                 │
└──────┬───────┘  └────────┬────────┘
       │                   │
       └───────────┬───────┘
                   │
                   ▼
            ┌─────────────────┐
            │  Database (H2)  │
            │                 │
            │  UPDATE clients │
            │  INSERT transfer│
            └─────────────────┘
```

### Ejemplo: GET /transfers/to/2 (Consultar Transferencias)

```
┌──────────────────────────────────────┐
│   Cliente HTTP                       │
│   GET /transfers/to/2                │
└────────────────┬─────────────────────┘
                 │
                 ▼
    ┌────────────────────────────────────┐
    │ TransferQueryController            │
    │ getTransfersTo(@PathVariable id)   │
    └────────────────┬───────────────────┘
                     │
                     ▼ (llama)
    ┌────────────────────────────────────────┐
    │ TransferQueryService                   │
    │ getTransfersByToClient(clientId)       │
    └────────────────┬──────────────────────┘
                     │
                     ▼ (llama)
    ┌────────────────────────────────────────┐
    │ TransferRepository                     │
    │ findTransfersByToClient(2)             │
    │ @Query("SELECT t FROM Transfer t      │
    │        WHERE t.toClient.id = :id")    │
    └────────────────┬──────────────────────┘
                     │
                     ▼
            ┌─────────────────┐
            │  Database (H2)  │
            │                 │
            │  SELECT * FROM  │
            │  transfers      │
            │  WHERE to_id=2  │
            └────────┬────────┘
                     │
                     ▼
    ┌────────────────────────────────────┐
    │ List<Transfer>                     │
    │ [Transfer{id:1, from:1, to:2...},  │
    │  Transfer{id:3, from:5, to:2...}]  │
    └────────────────┬───────────────────┘
                     │
    ┌────────────────┘
    │
    ▼ (mapToDto)
    ┌────────────────────────────────────┐
    │ List<TransferDto>                  │
    │ [{id:1, from:1, to:2, amount:100}, │
    │  {id:3, from:5, to:2, amount:250}] │
    └────────────────┬───────────────────┘
                     │
                     ▼
    ┌────────────────────────────────────┐
    │ JSON Response (200 OK)             │
    │ [{id:1, ...}, {id:3, ...}]         │
    └────────────────────────────────────┘
```

---

## 📦 Importancias de las Dependencias

```
┌────────────────────────────────────────────────────────────────────┐
│                    QUÉ NECESITA CADA CLASE                         │
└────────────────────────────────────────────────────────────────────┘

CONTROLLERS
│
├─ TransferController
│  ├─ Necesita: TransferCommandHandler
│  │  ├─ Para: procesar comandos de transferencia
│  │  └─ Tipo: @Autowired / Constructor Injection
│  │
│  └─ Tipo: @RestController (Spring lo inyecta automáticamente)
│
├─ TransferQueryController
│  ├─ Necesita: TransferQueryService
│  │  ├─ Para: consultar transferencias
│  │  └─ Tipo: @Autowired / Constructor Injection
│  │
│  └─ Tipo: @RestController
│
└─ ClientController
   ├─ Necesita: ClientQueryService
   │  ├─ Para: consultar clientes
   │  └─ Tipo: @Autowired / Constructor Injection
   │
   └─ Tipo: @RestController

HANDLERS (CQRS Command)
│
└─ TransferCommandHandler
   ├─ Necesita: TransferService
   │  ├─ Para: ejecutar la lógica de negocio
   │  └─ Tipo: @Autowired / Constructor Injection
   │
   └─ Tipo: @Component (Spring lo inyecta como bean)

SERVICES
│
├─ TransferService
│  ├─ Necesita: ClientRepository
│  │  ├─ Para: acceder a clientes en BD
│  │  └─ Tipo: @Autowired / Constructor Injection
│  │
│  ├─ Necesita: TransferRepository
│  │  ├─ Para: guardar transferencias en BD
│  │  └─ Tipo: @Autowired / Constructor Injection
│  │
│  └─ Tipo: @Service (Spring lo inyecta como bean)
│
├─ TransferQueryService (CQRS Query)
│  ├─ Necesita: TransferRepository
│  │  ├─ Para: consultar transferencias
│  │  └─ Tipo: @Autowired / Constructor Injection
│  │
│  └─ Tipo: @Service
│
└─ ClientQueryService (CQRS Query)
   ├─ Necesita: ClientRepository
   │  ├─ Para: consultar clientes
   │  └─ Tipo: @Autowired / Constructor Injection
   │
   └─ Tipo: @Service

REPOSITORIES
│
├─ ClientRepository
│  ├─ Hereda de: JpaRepository<Client, Long>
│  ├─ Proporciona: findById(), save(), findAll(), delete(), etc.
│  └─ Spring auto-registra como @Repository
│
└─ TransferRepository
   ├─ Hereda de: JpaRepository<Transfer, Long>
   ├─ Proporciona: findById(), save(), findAll(), delete(), etc.
   ├─ Métodos custom con @Query:
   │  ├─ findTransfersByFromClient(Long clientId)
   │  └─ findTransfersByToClient(Long clientId)
   │
   └─ Spring auto-registra como @Repository

ENTITIES (JPA)
│
├─ Client
│  ├─ Anotación: @Entity
│  ├─ Tabla: clients
│  ├─ Relación: One-to-Many con Transfer
│  └─ Mapeada por: Transfer.fromClient, Transfer.toClient
│
└─ Transfer
   ├─ Anotación: @Entity
   ├─ Tabla: transfers
   ├─ Relación: Many-to-One con Client
   │  ├─ @ManyToOne (fromClient)
   │  ├─ @JoinColumn(name="from_client_id")
   │  ├─ @ManyToOne (toClient)
   │  └─ @JoinColumn(name="to_client_id")
   │
   └─ Sin dependencias de otras clases

DTOS (Data Transfer Objects)
│
├─ ClientDto
│  └─ Solo datos: id, dni, name, balance
│
└─ TransferDto
   └─ Solo datos: id, fromClientId, toClientId, amount, timestamp
```

---

## 🔄 Ciclo de Vida de los Beans (Objetos Inyectados)

```
SPRING BOOT STARTUP
│
├─ 1. Detecta todas las clases con anotaciones
│  ├─ @Component
│  ├─ @Service
│  ├─ @Controller / @RestController
│  ├─ @Repository
│  └─ Interfaces que heredan de JpaRepository
│
├─ 2. Crea instancias (Beans) de cada clase
│  ├─ TransferController bean
│  ├─ TransferCommandHandler bean
│  ├─ TransferService bean
│  ├─ ClientQueryService bean
│  ├─ TransferQueryService bean
│  ├─ ClientRepository bean (auto-generado)
│  └─ TransferRepository bean (auto-generado)
│
├─ 3. Inyecta dependencias (Dependency Injection)
│  ├─ TransferController recibe TransferCommandHandler
│  ├─ TransferCommandHandler recibe TransferService
│  ├─ TransferService recibe ClientRepository + TransferRepository
│  └─ ... y así sucesivamente
│
├─ 4. Valida que todas las dependencias estén resueltas
│  └─ Si falta alguna → ERROR: NoSuchBeanDefinitionException
│
└─ 5. Aplicación lista para recibir peticiones HTTP
   └─ Los beans están disponibles en memoria listos para usar

CUANDO LLEGA UNA PETICIÓN HTTP
│
├─ 1. Spring DispatcherServlet enruta a TransferController
├─ 2. Inyecta la instancia de TransferCommandHandler
├─ 3. El método controller llama a handler.handle(command)
├─ 4. El handler inyecta la instancia de TransferService
├─ 5. El servicio obtiene los repositorios inyectados
├─ 6. Se realiza la lógica de negocio
└─ 7. Se retorna la respuesta al cliente
```

---

## 📊 Tabla de Responsabilidades y Dependencias

| Clase | Anotación | Responsabilidad | Necesita | Usada Por |
|-------|-----------|-----------------|----------|-----------|
| TransferController | @RestController | Recibir HTTP POST | TransferCommandHandler | Cliente HTTP |
| TransferCommandHandler | @Component | Procesar comando | TransferService | TransferController |
| TransferService | @Service | Lógica de negocio | ClientRepository, TransferRepository | TransferCommandHandler |
| ClientRepository | (auto) | Acceso datos de clientes | - | TransferService |
| TransferRepository | (auto) | Acceso datos de transferencias | - | TransferService, TransferQueryService |
| TransferQueryController | @RestController | Recibir HTTP GET | TransferQueryService | Cliente HTTP |
| TransferQueryService | @Service | Consultas de transferencias | TransferRepository | TransferQueryController |
| ClientController | @RestController | Recibir HTTP GET | ClientQueryService | Cliente HTTP |
| ClientQueryService | @Service | Consultas de clientes | ClientRepository | ClientController |
| Client | @Entity | Modelo de cliente | - | TransferService, Repositorios |
| Transfer | @Entity | Modelo de transferencia | - | TransferService, Repositorios |
| ClientDto | (simple) | Transferir datos al cliente | - | ClientQueryService |
| TransferDto | (simple) | Transferir datos al cliente | - | TransferQueryService |
| TransferMoneyCommand | (simple) | Transferir datos del cliente | - | TransferController |

---

## 🎯 Dependencia de Spring Framework

Todas las clases dependen de **Spring Boot**:

```
┌─────────────────────────────────────────────────┐
│         SPRING BOOT FRAMEWORK                   │
│                                                 │
│ ┌─────────────────────────────────────────────┐│
│ │ Spring Core                                 ││
│ │ - @Component, @Service, @Repository        ││
│ │ - Dependency Injection (DI)                 ││
│ │ - Beans Management                          ││
│ └─────────────────────────────────────────────┘│
│                                                 │
│ ┌─────────────────────────────────────────────┐│
│ │ Spring Web (MVC)                            ││
│ │ - @RestController, @PostMapping, @GetMapping││
│ │ - @PathVariable, @RequestBody              ││
│ │ - ResponseEntity                            ││
│ └─────────────────────────────────────────────┘│
│                                                 │
│ ┌─────────────────────────────────────────────┐│
│ │ Spring Data JPA                             ││
│ │ - JpaRepository                             ││
│ │ - @Entity, @Table, @Id                     ││
│ │ - @ManyToOne, @JoinColumn                 ││
│ │ - @Query                                    ││
│ └─────────────────────────────────────────────┘│
│                                                 │
│ ┌─────────────────────────────────────────────┐│
│ │ Spring Transaction                          ││
│ │ - @Transactional                            ││
│ │ - ACID Guarantees                           ││
│ └─────────────────────────────────────────────┘│
│                                                 │
└─────────────────────────────────────────────────┘

Usando:
- Jakarta EE (antes javax.*)
- Hibernate (JPA implementation)
- H2 Database
- Lombok (código limpio)
```

---

## 🔀 Relación entre Objetos en Memoria

```
CUANDO SE EJECUTA UNA TRANSFERENCIA:

Memoria de Spring:
┌──────────────────────────────────────────────────┐
│ Spring Context (IoC Container)                  │
│                                                  │
│ transferController ────┐                        │
│                        ├──> transferCommandHandler ──> transferService ─┐
│ transferQueryController─> transferQueryService ──> transferRepository  │
│                                                  │                      │
│ clientController ─────────> clientQueryService ─┼─> clientRepository   │
│                                                  │                      │
│                                                  └──> Database (H2)     │
│                                                                         │
│ transferMoneyCommand (new object created per request)                  │
│ clientDto, transferDto (created as needed)                             │
└──────────────────────────────────────────────────┘
```

**Explicación:**
- Los Controllers, Services, Handlers y Repositories son **Singletons** (una instancia en toda la aplicación)
- Los Commands y DTOs son **nuevos objetos** creados en cada petición
- Las Entities (Client, Transfer) se crean nuevas cuando se leen de BD

---

## 💾 Persistencia de Datos

```
Memoria de la Aplicación        Persistencia en BD
┌──────────────────────┐        ┌──────────────────┐
│ Client object #1     │        │ clients table    │
│ - id = 1             │◄──────►│ id=1             │
│ - name = "Juan"      │        │ name="Juan"      │
│ - balance = 400      │        │ balance=400      │
└──────────────────────┘        └──────────────────┘
     (Java Object)              (Persisted Row)

┌──────────────────────┐        ┌──────────────────┐
│ Transfer object #1   │        │ transfers table  │
│ - id = 1             │◄──────►│ id=1             │
│ - fromClient = Obj#1 │        │ from_client_id=1 │
│ - toClient = Obj#2   │        │ to_client_id=2   │
│ - amount = 100.0     │        │ amount=100.0     │
└──────────────────────┘        └──────────────────┘
  (Java Object)                 (Persisted Row)
```

---

*Diagrama de dependencias para entender mejor la arquitectura del proyecto* 🏗️
