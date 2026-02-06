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

## 🏗️ Arquitectura del Proyecto

El proyecto sigue una estructura organizada en capas:

```
src/main/java/com/drtx/bank/
├── command/
│   ├── handler/
│   │   └── TransferCommandHandler.java
│   └── model/
│       └── TransferMoneyCommand.java
├── controller/
│   └── TransferController.java
├── entity/
│   ├── Client.java
│   └── Transfer.java
├── query/
│   ├── dto/
│   │   └── ClientDto.java
│   └── service/
│       └── ClientQueryService.java
├── repository/
│   ├── ClientRepository.java
│   └── TransferRepository.java
├── service/
│   └── TransferService.java
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

### Endpoint de Transferencia
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

## 🚀 Estado Actual

- ✅ Estructura base completada
- ✅ Entidades JPA configuradas
- ✅ Repositorios implementados
- ✅ API REST básica funcional
- ✅ Patrón CQRS iniciado (carpetas command/query)
- ⚠️ Tests unitarios mínimos
- ⚠️ Validaciones de negocio pendientes (verificar saldos)
- ⚠️ Manejo de errores mejorable

## 📝 Próximas Mejoras Sugeridas

1. Implementar validación de transferencias:
   - Verificar saldo suficiente
   - Validar montos positivos
   - Validar que clientes existan

2. Mejorar manejo de excepciones y errores

3. Expandir el CQRS con más queries y handlers

4. Agregar tests unitarios e integración

5. Considerar transacciones ACID para transferencias

## 🏃 Cómo Ejecutar

```bash
# Compilar el proyecto
./mvnw clean install

# Ejecutar la aplicación
./mvnw spring-boot:run

# La aplicación estará disponible en http://localhost:8080
```

## 📦 Git

El repositorio está inicializado con Git (`.git/` presente) para control de versiones.

---

**Última actualización**: Febrero 2026
