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

```mermaid
graph TD
    Client([Cliente HTTP / Swagger UI])

    %% API Gateway como Puerta de Entrada Única
    subgraph APIGateway ["API Gateway (Spring Cloud Gateway)"]
        GW_Auth["Filter: JWT Validation & Header Injection\n(X-User-Id, X-User-Email)"]
        GW_Router{Path-Based Router}
    end

    Client -->|http://localhost:8080| GW_Auth
    GW_Auth --> GW_Router

    %% Microservicio Extraído
    subgraph MSNotifications ["Microservicio Extraído: ms-notifications"]
        NotifAPI["GET /notifications"]
        INotificationsService[Notification Core Logic]
        NotifDB[(notifications.* DB)]

        NotifAPI --> INotificationsService
        INotificationsService --> NotifDB
    end

    %% Monolito
    subgraph Monolith ["Monolito: finbank-monolith"]
        AuthAPI["POST /api/v1/auth/**"]
        AccAPI["GET, POST /accounts/**"]
        TrAPI["POST, GET /transfers"]
        AuditAPI["GET /audit"]

        subgraph AuthDomain ["Módulo Auth"]
            AuthAPI --> AuthUseCase
            AuthUseCase --> AuthDB[(auth.* DB)]
        end

        subgraph AccountsDomain ["Módulo Cuentas"]
            AccAPI --> AccountsUseCase
            AccountsUseCase --> IAccountsService
            IAccountsService --> AccountsDB[(accounts.* DB)]
        end

        subgraph TransfersDomain ["Módulo Transferencias"]
            TrAPI --> TransferUseCase
            TransferUseCase -->|Mismo proceso| IAccountsService
            TransferUseCase -->|Mismo proceso| IAuditService
            TransferUseCase --> TransfersDB[(transfers.* DB)]
        end

        subgraph AuditDomain ["Módulo Auditoría"]
            AuditAPI --> IAuditService
            IAuditService --> AuditDB[(audit.* DB)]
        end
    end

    %% Enrutamiento desde el Gateway (Strangler Fig)
    GW_Router -->|Ruta: /notifications/**| NotifAPI
    GW_Router -->|Ruta: /accounts/**, /audit/**, /api/v1/auth/**| Monolith
```

### Capas internas de cada módulo

```mermaid
graph LR
    subgraph módulo
        API["api/\n(Controller)"]
        APP["application/\n(UseCase + Interface)"]
        INFRA["infrastructure/\n(ServiceImpl + Repository)"]
        DOMAIN["domain/\n(Entity)"]
    end

    API --> APP
    APP --> DOMAIN
    INFRA --> APP
    INFRA --> DOMAIN

    subgraph "otros módulos"
        EXT["application/\n(Interface pública)"]
    end

    APP -.->|"solo a través\nde interfaces"| EXT
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
