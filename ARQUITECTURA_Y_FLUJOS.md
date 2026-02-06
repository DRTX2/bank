# 🏗️ Flujo y Relación de Clases del Proyecto Bank

## 📊 Arquitectura General

```
┌─────────────────────────────────────────────────────────────────────┐
│                           CLIENT (Postman/Browser)                   │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │      CONTROLLERS (API REST)          │
        │  ┌────────────────────────────────┐  │
        │  │  TransferController            │  │
        │  │  POST /transfer                │  │
        │  └────────────────────────────────┘  │
        │  ┌────────────────────────────────┐  │
        │  │  TransferQueryController       │  │
        │  │  GET /transfers/*              │  │
        │  └────────────────────────────────┘  │
        │  ┌────────────────────────────────┐  │
        │  │  ClientController              │  │
        │  │  GET /clients/*                │  │
        │  └────────────────────────────────┘  │
        └──────────────────────────────────────┘
                           │
        ┌──────────────────┴──────────────────┐
        │                                      │
        ▼                                      ▼
 ┌─────────────────┐              ┌──────────────────────┐
 │   COMMAND       │              │    QUERY (LECTURA)   │
 │   (ESCRITURA)   │              │                      │
 └────────┬────────┘              └──────────┬───────────┘
          │                                   │
          ▼                      ┌────────────┴──────────────┐
 ┌─────────────────────────┐     │                           │
 │ TransferCommandHandler  │     ▼                           ▼
 │ - handle(command)       │  ┌──────────────┐  ┌─────────────────────┐
 └────────────┬────────────┘  │ClientQuery   │  │TransferQueryService │
              │                │Service      │  │- getTransferById()  │
              ▼                │- getById()  │  │- getByFromClient()  │
       ┌──────────────────┐    │- getByDni() │  │- getByToClient()    │
       │ TransferService  │    │- getByName()│  │- getAllTransfers()  │
       │ - transfer()     │    └──────┬───────┘  └──────────┬──────────┘
       │   - validate()   │           │                      │
       │   - updateBal.   │           ▼                      ▼
       └────────┬─────────┘    ┌─────────────┐    ┌──────────────────┐
                │              │ repositories │    │  DTOs (Response) │
                ▼              │ (lectura)    │    │ ┌──────────────┐ │
        ┌────────────────┐     │              │    │ │ ClientDto    │ │
        │ repositories   │     └─────────────┬┘    │ │ TransferDto  │ │
        │ (escritura)    │                   │    │ └──────────────┘ │
        └────────┬───────┘                   │    └──────────────────┘
                 │                           │
    ┌────────────┴───────────────┬───────────┘
    │                            │
    ▼                            ▼
┌─────────────────┐    ┌──────────────────────┐
│ ClientRepository│    │TransferRepository    │
│ - save()        │    │ - findAll()          │
│ - findById()    │    │ - findById()         │
│ - findByDni()   │    │ - findByFromClient() │
│ - findByName()  │    │ - findByToClient()   │
└────────┬────────┘    └──────────┬───────────┘
         │                         │
         ▼                         ▼
    ┌─────────────────────────────────────┐
    │        DATABASE (H2)                 │
    │  ┌──────────────┐  ┌───────────────┐│
    │  │ clients      │  │ transfers     ││
    │  │ - id         │  │ - id          ││
    │  │ - dni        │  │ - from_client ││
    │  │ - name       │  │ - to_client   ││
    │  │ - balance    │  │ - amount      ││
    │  │              │  │ - timestamp   ││
    │  └──────────────┘  └───────────────┘│
    └─────────────────────────────────────┘
```

---

## 🔄 Flujo COMPLETO de una Transferencia

### **PASO 1: Cliente envía solicitud POST**

```
1. Cliente (Postman/Browser)
   ↓
POST /transfer
Content-Type: application/json
{
  "fromClientId": 1,
  "toClientId": 2,
  "amount": 100.0
}
```

---

### **PASO 2: TransferController recibe la solicitud**

```java
@RestController
@RequestMapping("/transfer")
public class TransferController {
    private final TransferCommandHandler transferCommandHandler;

    @PostMapping
    public ResponseEntity<String> transfer(@RequestBody TransferMoneyCommand command) {
        // ✅ TransferMoneyCommand se deserializa automáticamente
        //    { "fromClientId": 1, "toClientId": 2, "amount": 100.0 }
        
        transferCommandHandler.handle(command);  // ← PASO 3
        return ResponseEntity.ok("Transferencia realizada con éxito");
    }
}
```

**¿Qué pasa aquí?**
- Spring deserializa el JSON → Objeto `TransferMoneyCommand`
- El parámetro `command` contiene: `fromClientId=1, toClientId=2, amount=100.0`

---

### **PASO 3: TransferCommandHandler procesa el comando**

```java
@Component
public class TransferCommandHandler {
    private final TransferService transferService;

    @Transactional
    public void handle(TransferMoneyCommand command) {
        // ✅ Validación básica
        if (command == null) {
            throw new IllegalArgumentException("Transfer command cannot be null");
        }
        
        // ✅ Delega al servicio
        transferService.transfer(
            command.getFromClientId(),      // 1
            command.getToClientId(),        // 2
            command.getAmount()             // 100.0
        );  // ← PASO 4
    }
}
```

**¿Qué pasa aquí?**
- El handler extrae los valores del comando
- Los pasa al servicio de transferencia

---

### **PASO 4: TransferService realiza la transferencia**

```java
@Service
public class TransferService {
    private final ClientRepository clientRepository;
    private final TransferRepository transferRepository;

    @Transactional
    public void transfer(Long fromClientId, Long toClientId, Double amount) {
        
        // 1️⃣ VALIDACIONES
        validateTransferRequest(fromClientId, toClientId, amount);
        
        // 2️⃣ OBTENER CLIENTES
        Client from = clientRepository.findById(fromClientId)
                .orElseThrow(() -> new RuntimeException("Origin client not found"));
        
        Client to = clientRepository.findById(toClientId)
                .orElseThrow(() -> new RuntimeException("Destination client not found"));
        
        // 3️⃣ VALIDAR LÓGICA DE NEGOCIO
        if (from.getId().equals(to.getId())) {
            throw new RuntimeException("Cannot transfer to same client");
        }
        
        if (from.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }
        
        // 4️⃣ ACTUALIZAR SALDOS
        from.setBalance(from.getBalance() - amount);  // Cliente 1: 500 - 100 = 400
        to.setBalance(to.getBalance() + amount);      // Cliente 2: 300 + 100 = 400
        
        // 5️⃣ GUARDAR CAMBIOS EN BD
        clientRepository.save(from);
        clientRepository.save(to);
        
        // 6️⃣ REGISTRAR TRANSFERENCIA
        Transfer transfer = new Transfer();
        transfer.setFromClient(from);
        transfer.setToClient(to);
        transfer.setAmount(amount);
        transfer.setTimestamp(LocalDateTime.now());
        
        transferRepository.save(transfer);  // ← GUARDADO EN BD
    }
    
    // Validaciones
    private void validateTransferRequest(Long fromClientId, Long toClientId, Double amount) {
        if (fromClientId == null) throw new IllegalArgumentException("fromClientId is null");
        if (toClientId == null) throw new IllegalArgumentException("toClientId is null");
        if (amount == null) throw new IllegalArgumentException("amount is null");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (amount < 0.01) throw new IllegalArgumentException("amount must be >= 0.01");
    }
}
```

**¿Qué pasa aquí?**
- Valida todos los datos
- Obtiene los clientes de la BD
- Verifica que haya saldo suficiente
- Actualiza los saldos
- Guarda la nueva transferencia en la BD

---

### **PASO 5: Los Repositorios interactúan con la BD**

```
TransferService llama:

1. clientRepository.findById(1)
   └─> Base de datos: SELECT * FROM clients WHERE id = 1
       Retorna: Cliente { id: 1, name: "Juan", balance: 500.0 }

2. clientRepository.findById(2)
   └─> Base de datos: SELECT * FROM clients WHERE id = 2
       Retorna: Cliente { id: 2, name: "María", balance: 300.0 }

3. clientRepository.save(from)  // Juan con balance 400
   └─> Base de datos: UPDATE clients SET balance = 400 WHERE id = 1

4. clientRepository.save(to)    // María con balance 400
   └─> Base de datos: UPDATE clients SET balance = 400 WHERE id = 2

5. transferRepository.save(transfer)
   └─> Base de datos: INSERT INTO transfers (from_client, to_client, amount, timestamp)
       VALUES (1, 2, 100.0, 2026-02-06T09:50:00)
```

---

## 📖 Flujo COMPLETO de una CONSULTA (GET)

### **Escenario: Cliente quiere consultar sus transferencias recibidas**

```
1. Cliente (Browser)
   ↓
GET /transfers/to/2
```

---

### **PASO 1: TransferQueryController recibe la solicitud**

```java
@RestController
@RequestMapping("/transfers")
public class TransferQueryController {
    private final TransferQueryService transferQueryService;

    @GetMapping("/to/{clientId}")
    public ResponseEntity<List<TransferDto>> getTransfersTo(@PathVariable Long clientId) {
        List<TransferDto> transfers = transferQueryService.getTransfersByToClient(clientId);
        // ← PASO 2
        return ResponseEntity.ok(transfers);
    }
}
```

**¿Qué pasa aquí?**
- Spring extrae el parámetro: `clientId = 2`
- Llama al servicio de consulta

---

### **PASO 2: TransferQueryService realiza la búsqueda**

```java
@Service
public class TransferQueryService {
    private final TransferRepository transferRepository;

    public List<TransferDto> getTransfersByToClient(Long clientId) {
        // 1️⃣ Consulta la BD
        List<Transfer> transfers = transferRepository.findTransfersByToClient(clientId);
        // ← PASO 3 (consulta a BD)
        
        // 2️⃣ Convierte Entities a DTOs
        return transfers.stream()
                .map(this::mapToDto)
                .toList();
    }

    private TransferDto mapToDto(Transfer transfer) {
        return new TransferDto(
            transfer.getId(),
            transfer.getFromClient().getId(),
            transfer.getToClient().getId(),
            transfer.getAmount(),
            transfer.getTimestamp()
        );
    }
}
```

**¿Qué pasa aquí?**
- Llama al repositorio para obtener las transferencias
- Convierte objetos Entity en DTOs (para la respuesta)

---

### **PASO 3: El Repositorio consulta la BD**

```java
@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
    @Query("SELECT t FROM Transfer t WHERE t.toClient.id = :clientId")
    List<Transfer> findTransfersByToClient(@Param("clientId") Long clientId);
}
```

**Consulta SQL que se ejecuta:**
```sql
SELECT * FROM transfers 
WHERE to_client_id = 2
```

**Resultado de la BD:**
```
id  from_client_id  to_client_id  amount   timestamp
1   1               2             100.0    2026-02-06 09:50:00
3   5               2             250.0    2026-02-06 10:15:00
```

---

### **PASO 4: Respuesta JSON al Cliente**

```java
// Los DTOs se convierten a JSON automáticamente
[
  {
    "id": 1,
    "fromClientId": 1,
    "toClientId": 2,
    "amount": 100.0,
    "timestamp": "2026-02-06T09:50:00"
  },
  {
    "id": 3,
    "fromClientId": 5,
    "toClientId": 2,
    "amount": 250.0,
    "timestamp": "2026-02-06T10:15:00"
  }
]
```

---

## 🎯 Diagrama de Relaciones entre Entidades

```
┌──────────────────────────┐
│       CLIENT             │
├──────────────────────────┤
│ - id (PK)                │
│ - dni                    │
│ - name                   │
│ - balance                │
└──────────────┬───────────┘
               │
        ┌──────┴──────────┬─────────────────┐
        │                 │                 │
        │ (fromClient)    │ (toClient)      │
        │ One-to-Many     │ One-to-Many     │
        │                 │                 │
        ▼                 ▼                 │
  ┌──────────────────────────────────┐    │
  │        TRANSFER                   │    │
  ├──────────────────────────────────┤    │
  │ - id (PK)                        │◄───┘
  │ - from_client_id (FK) ──────────────┐
  │ - to_client_id (FK) ───────────────┐│
  │ - amount                          ││
  │ - timestamp                       ││
  └──────────────────────────────────┘└┘

Ejemplo:
┌─────────────────────┬──────────────────┐
│  CLIENT             │   TRANSFER       │
├─────────────────────┼──────────────────┤
│ id=1 (Juan)         │  id=1            │
│ balance=400         │  from=1 (Juan)   │
│                     │  to=2 (María)    │
│ id=2 (María)        │  amount=100      │
│ balance=400         │  time=09:50:00   │
│                     │                  │
│ id=5 (Pedro)        │  id=3            │
│ balance=250         │  from=5 (Pedro)  │
└─────────────────────┤  to=2 (María)    │
                      │  amount=250      │
                      │  time=10:15:00   │
                      └──────────────────┘
```

---

## 📝 Resumen de Responsabilidades

| Clase | Responsabilidad |
|-------|-----------------|
| **TransferController** | Recibe solicitudes HTTP POST y delega al handler |
| **TransferCommandHandler** | Procesa comandos de transferencia (CQRS Command) |
| **TransferService** | Lógica de negocio: validaciones y actualización de datos |
| **ClientRepository** | Acceso a datos de clientes (SELECT, INSERT, UPDATE) |
| **TransferRepository** | Acceso a datos de transferencias (SELECT, INSERT) |
| **TransferQueryController** | Recibe solicitudes HTTP GET |
| **TransferQueryService** | Consultas de transferencias (CQRS Query) |
| **ClientQueryService** | Consultas de clientes (CQRS Query) |
| **ClientDto** | Objeto para respuesta de clientes (sin datos sensibles) |
| **TransferDto** | Objeto para respuesta de transferencias |
| **Client (Entity)** | Modelo de datos de cliente en BD |
| **Transfer (Entity)** | Modelo de datos de transferencia en BD |

---

## 🔐 Separación CQRS (Command Query Responsibility Segregation)

### **WRITE (COMMAND)**
```
POST /transfer
└─> TransferController
    └─> TransferCommandHandler
        └─> TransferService
            └─> ClientRepository.save()
            └─> TransferRepository.save()
```
**Responsabilidad**: Modificar datos en la BD

---

### **READ (QUERY)**
```
GET /transfers/to/{clientId}
└─> TransferQueryController
    └─> TransferQueryService
        └─> TransferRepository.findByToClient()
            └─> (Solo lectura, sin modificaciones)
```
**Responsabilidad**: Consultar datos sin modificarlos

---

## 🌊 Flujo de Datos de una Transferencia paso a paso

```
OBJETO JSON ENTRADA:
{
  "fromClientId": 1,
  "toClientId": 2,
  "amount": 100.0
}
            │
            ▼
┌───────────────────────────┐
│ TransferMoneyCommand      │
│ - fromClientId: 1         │
│ - toClientId: 2           │
│ - amount: 100.0           │
└───────────┬───────────────┘
            │
            ▼
┌───────────────────────────────────────┐
│ TransferCommandHandler.handle()        │
│ (valida que command != null)           │
└───────────┬───────────────────────────┘
            │
            ▼
┌───────────────────────────────────────┐
│ TransferService.transfer()             │
│ - validateTransferRequest()            │
│ - clientRepository.findById(1)         │
│ - clientRepository.findById(2)         │
│ - Actualizar saldos                   │
│ - clientRepository.save()              │
│ - Crear Transfer object                │
│ - transferRepository.save()            │
└───────────┬───────────────────────────┘
            │
            ▼
┌──────────────────────────────────────┐
│ BASE DE DATOS ACTUALIZADA            │
│                                      │
│ clients:                             │
│ id=1, balance=400 (antes era 500)    │
│ id=2, balance=400 (antes era 300)    │
│                                      │
│ transfers (new row):                 │
│ id=1, from=1, to=2, amount=100       │
└──────────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────┐
│ RESPUESTA AL CLIENTE                 │
│ HTTP 200 OK                          │
│ "Transferencia realizada con éxito"  │
└──────────────────────────────────────┘
```

---

## 💡 Ejemplo Concreto: Transferencia de Juan a María

```
INICIO:
┌──────────┐                ┌──────────┐
│ Juan     │                │ María    │
│ balance: │                │ balance: │
│  500.0   │                │  300.0   │
└──────────┘                └──────────┘

PETICIÓN:
POST /transfer
{
  "fromClientId": 1,    ← Juan
  "toClientId": 2,      ← María
  "amount": 100.0
}

PROCESAMIENTO:
1. TransferController recibe comando JSON
2. Deserializa a TransferMoneyCommand
3. TransferCommandHandler valida
4. TransferService:
   - Obtiene Juan de BD (balance: 500)
   - Obtiene María de BD (balance: 300)
   - Valida: Juan tiene 500 >= 100? ✅
   - Actualiza: Juan.balance = 500 - 100 = 400
   - Actualiza: María.balance = 300 + 100 = 400
   - Guarda ambos en BD
   - Crea Transfer y guarda
5. Transacción completada (ACID)

FINAL:
┌──────────┐                ┌──────────┐
│ Juan     │                │ María    │
│ balance: │                │ balance: │
│  400.0   │ ───100──────> │  400.0   │
└──────────┘                └──────────┘

BD ACTUALIZADA:
clients table:
id  name   balance
1   Juan   400.0     ← Actualizado
2   María  400.0     ← Actualizado

transfers table:
id  from  to  amount  timestamp
1   1     2   100.0   2026-02-06 09:50:00  ← Nuevo
```

---

## 🎓 Conclusión

**Flujo COMMAND (escritura):**
```
API HTTP → Controller → CommandHandler → Service → Repositories → BD
```

**Flujo QUERY (lectura):**
```
API HTTP → Controller → QueryService → Repository → BD → DTO → JSON
```

**Patrón CQRS:**
- Separación clara entre Command (modificación) y Query (lectura)
- Mejor mantenibilidad y escalabilidad
- Cada responsabilidad tiene su propio flujo

---

*Diagrama creado para ayudarte a entender la arquitectura del proyecto Bank* 🏦
