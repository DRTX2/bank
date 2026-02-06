# Banco - Proyecto Spring Boot

## 📋 Descripción General

**Banco** es una aplicación de demostración desarrollada con **Spring Boot** que implementa un sistema bancario básico para gestionar transferencias de dinero entre clientes.

## 🎯 Propósito

Este proyecto es un **ejercicio educativo** que demuestra:
- Arquitectura en capas con Spring Boot
- Manejo de entidades JPA con relaciones Many-to-One
- Implementación del patrón CQRS (Command Query Responsibility Segregation)
- Operaciones CRUD básicas de transferencias bancarias
- Gestión de DTOs y servicios
- Validaciones de negocio robustas

## 🏗️ Arquitectura del Proyecto

El proyecto sigue una estructura organizada en capas:

```
src/main/java/com/drtx/bank/
├── command/
│   ├── handler/
│   │   └── TransferCommandHandler.java        ✅ Completo
│   └── model/
│       └── TransferMoneyCommand.java          ✅ Completo
├── controller/
│   ├── ClientController.java                  ✅ Completo
│   ├── TransferController.java                ✅ Completo
│   └── TransferQueryController.java           ✅ Completo
├── entity/
│   ├── Client.java                            ✅ Completo
│   └── Transfer.java                          ✅ Completo
├── query/
│   ├── dto/
│   │   ├── ClientDto.java                     ✅ Completo
│   │   └── TransferDto.java                   ✅ Completo
│   └── service/
│       ├── ClientQueryService.java            ✅ Completo
│       └── TransferQueryService.java          ✅ Completo
├── repository/
│   ├── ClientRepository.java                  ✅ Mejorado
│   └── TransferRepository.java                ✅ Mejorado
├── service/
│   └── TransferService.java                   ✅ Mejorado
└── BankApplication.java
```

## 📊 Entidades Principales

### Client (Cliente)
Representa un cliente del banco con:
- **id**: Identificador único (auto-generado)
- **dni**: Documento de identidad
- **name**: Nombre del cliente
- **balance**: Saldo disponible en la cuenta

### Transfer (Transferencia)
Registra cada transferencia bancaria:
- **id**: Identificador único (auto-generado)
- **fromClient**: Cliente origen (relación Many-to-One)
- **toClient**: Cliente destino (relación Many-to-One)
- **amount**: Monto transferido
- **timestamp**: Fecha y hora de la transferencia

## 🔌 API REST

### Endpoints de Transferencia

#### Crear Transferencia (COMMAND)
- **POST** `/transfer`
- **Body (JSON)**:
  ```json
  {
    "fromClientId": 1,
    "toClientId": 2,
    "amount": 100.0
  }
  ```
- **Response**: `"Transferencia realizada con éxito"`

#### Consultar Transferencia (QUERY)
- **GET** `/transfers/{transferId}`
- **Response**: Detalles de la transferencia

#### Consultar Transferencias por Cliente
- **GET** `/transfers/from/{clientId}` - Transferencias enviadas
- **GET** `/transfers/to/{clientId}` - Transferencias recibidas
- **GET** `/transfers` - Todas las transferencias

### Endpoints de Clientes

#### Obtener Cliente por ID
- **GET** `/clients/{clientId}`
- **Response**: Datos del cliente (id, dni, name, balance)

#### Obtener Cliente por DNI
- **GET** `/clients/dni/{dni}`

#### Buscar Clientes por Nombre
- **GET** `/clients/search?name=Juan`

#### Obtener Todos los Clientes
- **GET** `/clients`

## 🗄️ Base de Datos

- **Tipo**: H2 (Base de datos en memoria)
- **URL JDBC**: `jdbc:h2:mem:bankdb`
- **Consola H2**: http://localhost:8080/h2-console
- **Usuario**: `sa` (sin contraseña)

**Características**:
- Auto-creación de tablas con Hibernate (`ddl-auto: update`)
- SQL logging habilitado para depuración
- Ideal para desarrollo y testing

## 🛠️ Stack Tecnológico

- **Framework**: Spring Boot 3.4.4
- **Java**: Versión 21
- **Persistencia**: JPA + Hibernate
- **Base de Datos**: H2
- **Build**: Maven
- **Dependencias clave**:
  - spring-boot-starter-data-jpa
  - spring-boot-starter-web
  - lombok (reducción de boilerplate)
  - h2 (base de datos en memoria)

## ✅ Estado Actual

- ✅ Estructura base completada
- ✅ Entidades JPA configuradas
- ✅ Repositorios implementados con métodos de búsqueda
- ✅ API REST completamente funcional
- ✅ Patrón CQRS completamente implementado
  - ✅ Command: TransferCommandHandler procesa comandos de transferencia
  - ✅ Query: TransferQueryService y ClientQueryService para consultas
- ✅ DTOs completos (ClientDto, TransferDto)
- ✅ Validaciones robustas de negocio:
  - Verificar saldo suficiente
  - Validar montos positivos y mínimos
  - Validar que clientes existan
  - Prevenir transferencias a mismo cliente
  - Manejo detallado de errores
- ✅ Transacciones ACID para transferencias (@Transactional)
- ✅ Búsqueda avanzada de clientes (por nombre, DNI)
- ✅ Búsqueda avanzada de transferencias (por cliente origen/destino)

## 📝 Características Implementadas

### Command Pattern (CQRS)
- **TransferCommandHandler**: Procesa comandos de transferencia
- **TransferMoneyCommand**: DTO que encapsula los datos de comando
- Separación clara entre lectura y escritura

### Query Pattern (CQRS)
- **ClientQueryService**: Servicio especializado para consultas de clientes
- **TransferQueryService**: Servicio especializado para consultas de transferencias
- DTOs dedicados para respuestas de lectura

### Validaciones Implementadas
- Montos válidos (> 0, >= 0.01)
- Clientes existentes
- Saldo suficiente en cliente origen
- Prevención de auto-transferencias
- Nulidad de parámetros

### Transaccionalidad
- `@Transactional` en métodos críticos
- Garantiza consistencia de datos
- Actualización atómica de saldos y registro de transferencias

## 🚀 Próximas Mejoras Posibles

1. **Seguridad**: Implementar Spring Security + JWT (mencionado como futuro)
2. **Validaciones avanzadas**: 
   - Límites de transferencia diaria
   - Tipos de clientes diferentes
3. **Auditoría**: Registrar quién realizó cada operación
4. **Eventos**: Event sourcing para cambios en cuentas
5. **Testing**: Suite completa de tests unitarios e integración
6. **Documentación API**: Swagger/OpenAPI
7. **Manejo de excepciones**: Custom exceptions y ControllerAdvice
8. **Paginación**: Para listados grandes de transferencias

## 🏃 Cómo Ejecutar

```bash
# Compilar el proyecto
./mvnw clean install

# Ejecutar la aplicación
./mvnw spring-boot:run

# La aplicación estará disponible en http://localhost:8080
```

### Ejemplo de Uso

```bash
# Crear una transferencia
curl -X POST http://localhost:8080/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromClientId": 1, "toClientId": 2, "amount": 100.0}'

# Obtener información de un cliente
curl http://localhost:8080/clients/1

# Obtener todas las transferencias
curl http://localhost:8080/transfers

# Buscar transferencias enviadas por cliente 1
curl http://localhost:8080/transfers/from/1
```

## 📦 Git

El repositorio está inicializado con Git (`.git/` presente) para control de versiones.

---

**Última actualización**: Febrero 2026
**Estado**: ✅ Completado - Listo para uso educativo

