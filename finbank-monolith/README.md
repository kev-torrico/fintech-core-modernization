# modular-bank-java

Monolito modular bancario implementado en Java / Spring Boot 3. Referencia técnica para migración a microservicios.

## Requisitos

- Java 17+
- Maven 3.9+
- Docker

## Ejecutar

```bash
docker-compose up -d
mvn spring-boot:run
```

## Módulos

| Módulo        | Schema           | Interfaz pública     |
| ------------- | ---------------- | -------------------- |
| auth          | auth.\*          | — (solo JWT)         |
| accounts      | accounts.\*      | AccountsService      |
| transfers     | transfers.\*     | — (orchestrador)     |
| notifications | notifications.\* | NotificationsService |
| audit         | audit.\*         | AuditService         |

## Arquitectura

### Dependencias entre módulos

```mermaid
graph TD
    Client([Cliente HTTP])

    Client --> AuthAPI[POST /auth/**]
    Client --> AccAPI["GET, POST /accounts/**"]
    Client --> TrAPI["POST, GET /transfers"]
    Client --> NotifAPI[GET /notifications]
    Client --> AuditAPI[GET /audit]

    subgraph Auth
        AuthAPI --> AuthUseCase
        AuthUseCase --> AuthDB[(auth.*)]
    end

    subgraph Accounts
        AccAPI --> AccountsUseCase
        AccountsUseCase --> IAccountsService
        IAccountsService --> AccountsDB[(accounts.*)]
    end

    subgraph Transfers
        TrAPI --> TransferUseCase
        TransferUseCase -->|IAccountsService| IAccountsService
        TransferUseCase -->|INotificationsService| INotificationsService
        TransferUseCase -->|IAuditService| IAuditService
        TransferUseCase --> TransfersDB[(transfers.*)]
    end

    subgraph Notifications
        NotifAPI --> INotificationsService
        INotificationsService --> NotifDB[(notifications.*)]
    end

    subgraph Audit
        AuditAPI --> IAuditService
        IAuditService --> AuditDB[(audit.*)]
    end
```

### Patron de Consistencia Distribuida

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente / API Gateway
    participant TxService as ms-transfers
    participant TxDB as postgres-transfers
    participant Kafka as Apache Kafka
    participant NotifService as ms-notifications
    participant NotifDB as postgres-notifications
    participant AuditService as finbank-monolith (Audit)
    participant MonolithDB as postgres-monolith

    rect rgb(235, 245, 255)
        Cliente->>TxService: POST /api/v1/transfers
        TxService->>TxDB: BEGIN TRANSACTION
        TxService->>TxDB: INSERT INTO transfers (status = 'COMPLETED')
        TxService->>TxDB: COMMIT
        TxService-->>Cliente: 201 CREATED (Respuesta Inmediata)
    end

    rect rgb(240, 253, 244)
        par Publicación de Eventos
            TxService->>Kafka: Publish (topic: notification-events)
        and
            TxService->>Kafka: Publish (topic: transfer-events)
        end

        par Consumo ms-notifications
            Kafka->>NotifService: Consume (NotificationEvent)
            NotifService->>NotifDB: INSERT INTO notifications
        and Consumo Módulo Auditoría
            Kafka->>AuditService: Consume (TransferAuditEvent)
            AuditService->>MonolithDB: INSERT INTO audit_entries
        end
    end

```

### Diagrama de Secuencia: Happy Path: Failure Path

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente HTTP
    participant GW as API Gateway
    participant Transf as ms-transfers
    participant Monolith as finbank-monolith (Accounts)
    participant Kafka as Apache Kafka
    participant Notif as ms-notifications

    alt HAPPY PATH (Con Idempotencia)
        Cliente->>GW: POST /api/v1/transfers (Header: X-Idempotency-Key: 123)
        GW->>Transf: Forward Request
        Transf->>Transf: Check Idempotency Key (No procesada)
        Transf->>Monolith: GET /api/v1/accounts (Validación HTTP)
        Monolith-->>Transf: 200 OK (Cuentas válidas)
        Transf->>Transf: Guardar Transferencia en postgres-transfers
        Transf->>Kafka: Publish Events (notification-events & transfer-events)
        Transf-->>Cliente: 201 CREATED
    else FAILURE PATH (Fallos de Red & Circuit Breaker / Retry)
        Cliente->>GW: POST /api/v1/transfers
        GW->>Transf: Forward Request
        Note over Transf, Monolith: Caída transitoria del Monolito
        loop Retry + Backoff Exponencial (3 Intentos)
            Transf->>Monolith: GET /api/v1/accounts (Falla / Timeout)
        end
        Note over Transf: Circuit Breaker se abre (OPEN State)
        Transf->>Transf: Exec Fallback Method
        Transf-->>Cliente: 503 Service Unavailable (Respuesta Degradada Controlada)
    end
```

### Capas internas de cada módulo

```mermaid
graph TD
    Client([Cliente HTTP / Postman / Swagger UI])

    %% API Gateway
    subgraph APIGateway ["API Gateway (Spring Cloud Gateway)"]
        GW_Auth["Filter: JWT Authentication & Header Injection<br/>(X-User-Id, X-User-Roles)"]
        GW_Router{Path-Based Router}
    end

    Client -->|http://localhost:8080| GW_Auth
    GW_Auth --> GW_Router

    %% Broker de Mensajería Kafka
    subgraph MessageBroker ["Message Broker (Apache Kafka)"]
        TopicNotif["Topic: notification-events"]
        TopicAudit["Topic: transfer-events"]
    end

    %% MS1: Microservicio de Notificaciones
    subgraph MS1 ["MS1: ms-notifications"]
        NotifConsumer["Kafka Consumer<br/>(groupId: ms-notifications)"]
        NotifAPI["GET /api/v1/notifications"]
        NotifDB[(postgres-notifications)]

        NotifConsumer -->|Guarda notificación| NotifDB
        NotifAPI --> NotifDB
    end

    %% MS2: Microservicio de Transferencias
    subgraph MS2 ["MS2: ms-transfers (Arquitectura Hexagonal)"]
        TransfAPI["POST, GET /api/v1/transfers"]
        TransfUseCase[TransfersUseCase / Domain Logic]
        AccountsHttpClient["Adapters/Out/Http<br/>(AccountsHttpClient - WebClient)"]
        KafkaProducer["Adapters/Out/Kafka<br/>(KafkaTransferEventPublisher)"]
        TransfDB[(postgres-transfers)]

        TransfAPI --> TransfUseCase
        TransfUseCase -->|1. Valida cuentas vía HTTP| AccountsHttpClient
        TransfUseCase -->|2. ACID Transaction| TransfDB
        TransfUseCase -->|3. Emite eventos| KafkaProducer
    end

    %% Monolito Remanente
    subgraph Monolith ["Monolito Remanente: finbank-monolith"]
        AuthAPI["POST /api/v1/auth/**"]
        AccAPI["GET /api/v1/accounts<br/>GET /accounts/{id}/exists"]
        AuditListener["Kafka Consumer / Audit Listener<br/>(groupId: finbank-monolith-audit)"]
        MonolithDB[(postgres-monolith)]

        AuthAPI --> MonolithDB
        AccAPI --> MonolithDB
        AuditListener -->|Guarda traza de auditoría| MonolithDB
    end

    %% Enrutamiento HTTP desde el Gateway
    GW_Router -->|/api/v1/notifications/**| NotifAPI
    GW_Router -->|/api/v1/transfers/**| TransfAPI
    GW_Router -->|/api/v1/auth/**, /api/v1/accounts/**| Monolith

    %% Integración HTTP Síncrona (Validación de Cuentas)
    AccountsHttpClient -->|GET /api/v1/accounts<br/>Bearer Token Forwarding| GW_Router

    %% Integración Asíncrona (Event-Driven Architecture)
    KafkaProducer -->|NotificationEvent| TopicNotif
    KafkaProducer -->|TransferAuditEvent| TopicAudit

    TopicNotif -->|Suscripción| NotifConsumer
    TopicAudit -->|Suscripción| AuditListener
```

### Aislamiento de schemas en PostgreSQL

```mermaid
graph TD
    subgraph PostgreSQL
        subgraph auth
            users[(users)]
            refresh_tokens[(refresh_tokens)]
        end
        subgraph accounts
            accounts_t[(accounts)]
        end
        subgraph transfers
            transfers_t[(transfers)]
        end
        subgraph notifications
            notifications_t[(notifications)]
        end
        subgraph audit
            audit_entries[(audit_entries)]
        end
    end
```
