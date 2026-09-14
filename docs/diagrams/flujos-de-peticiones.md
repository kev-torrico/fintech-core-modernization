# Diagramas de secuencia — Flujos de petición

Este documento reúne los diagramas de secuencia de los flujos de petición más
relevantes del proyecto: los que atraviesan el `api-gateway`, el monolito remanente y
los dos microservicios extraídos (`ms-transfers`, `ms-notifications`), tanto por HTTP
como por Kafka. Complementa el [diagrama general](../../README.md#diagrama-general-del-proyecto)
del README raíz (que muestra la topología estática) con la secuencia temporal de cada
caso de uso.

> - [Patrón de consistencia distribuida (Saga)](../../finbank-monolith/README.md#patron-de-consistencia-distribuida) — publicación y consumo paralelo de `NotificationEvent`/`TransferAuditEvent` tras crear una transferencia.
> - [Happy Path vs Failure Path](../../finbank-monolith/README.md#diagrama-de-secuencia-happy-path-vs-failure-path) — la misma transferencia con el Monolito caído, Circuit Breaker abierto y respuesta degradada.
>
> Los diagramas de abajo cubren el resto de flujos del sistema: autenticación, cuentas,
> el detalle interno de `TransfersUseCase` (idempotencia, validaciones, condición de
> carrera), el reintento del consumidor Kafka de `ms-notifications` y las consultas de
> lectura (notificaciones, auditoría).

## Tabla de contenido

- [1. Autenticación — Registro y Login](#1-autenticación--registro-y-login)
- [2. Gestión de cuentas — Crear y listar](#2-gestión-de-cuentas--crear-y-listar)
- [3. Transferencia — Happy path con validaciones e idempotencia](#3-transferencia--happy-path-con-validaciones-e-idempotencia)
- [4. Transferencia — Reintento con la misma clave de idempotencia](#4-transferencia--reintento-con-la-misma-clave-de-idempotencia)
- [5. Circuit Breaker + Retry ante caída del monolito](#5-circuit-breaker--retry-ante-caída-del-monolito)
- [6. Consumo de eventos en `ms-notifications` con reintento y descarte](#6-consumo-de-eventos-en-ms-notifications-con-reintento-y-descarte)
- [7. Consulta de notificaciones y auditoría (lectura)](#7-consulta-de-notificaciones-y-auditoría-lectura)

---

## 1. Autenticación — Registro y Login

`POST /api/v1/auth/register` y `POST /api/v1/auth/login` son las únicas rutas públicas:
el Gateway las enruta sin pasar por `AuthenticationFilter` (ver ruta `monolith-public-auth`
en [`api-gateway/src/main/resources/application.yml`](../../api-gateway/src/main/resources/application.yml)).

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente HTTP
    participant GW as API Gateway
    participant Auth as finbank-monolith (Auth)
    participant DB as postgres-monolith (auth.*)

    Cliente->>GW: POST /api/v1/auth/register {email, password, name}
    Note over GW: Ruta pública — sin AuthenticationFilter<br/>StripPrefix=2 -> /auth/register
    GW->>Auth: POST /auth/register
    Auth->>DB: INSERT INTO users
    Auth-->>GW: 200 OK { accessToken, refreshToken }
    GW-->>Cliente: 200 OK

    Cliente->>GW: POST /api/v1/auth/login {email, password}
    GW->>Auth: POST /auth/login
    Auth->>DB: SELECT users WHERE email = ?
    Auth->>Auth: Verifica password (BCrypt) y firma JWT
    Auth-->>GW: 200 OK { accessToken, refreshToken }
    GW-->>Cliente: 200 OK
```

## 2. Gestión de cuentas — Crear y listar

Rutas protegidas del monolito (`monolith-protected-route`): pasan por
`AuthenticationFilter`, que valida el JWT y **propaga** `X-User-Id` / `X-User-Roles`
hacia el servicio downstream — el controlador nunca ve el token, solo el `userId` ya
resuelto.

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente HTTP
    participant GW as API Gateway
    participant AuthF as AuthenticationFilter
    participant Acc as finbank-monolith (Accounts)
    participant DB as postgres-monolith (accounts.*)

    Cliente->>GW: POST /api/v1/accounts (Authorization: Bearer <token>)
    GW->>AuthF: Validar JWT
    AuthF->>AuthF: parseAndValidate(token) -> claims (sub, email, roles)
    AuthF->>Acc: POST /accounts (X-User-Id, X-User-Email, X-User-Roles)
    Acc->>DB: INSERT INTO accounts (owner_id = X-User-Id)
    Acc-->>GW: 201 CREATED { id, ownerId, balance }
    GW-->>Cliente: 201 CREATED

    Cliente->>GW: GET /api/v1/accounts (Authorization: Bearer <token>)
    GW->>AuthF: Validar JWT
    AuthF->>Acc: GET /accounts (X-User-Id, ...)
    Acc->>DB: SELECT accounts WHERE owner_id = X-User-Id
    Acc-->>GW: 200 OK [ {id, balance}, ... ]
    GW-->>Cliente: 200 OK
```

## 3. Transferencia — Happy path con validaciones e idempotencia

Detalle interno de `TransfersUseCase.execute(...)` en `ms-transfers`: el orden real de
las validaciones (idempotencia → cuentas distintas → titularidad del origen →
existencia del destino → saldo) antes de persistir y publicar eventos. Complementa el
diagrama de Saga ya existente en `finbank-monolith/README.md`, mostrando qué pasa
_dentro_ de `ms-transfers` antes de que se publique cualquier evento.

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente HTTP
    participant GW as API Gateway
    participant UC as ms-transfers (TransfersUseCase)
    participant TrDB as postgres-transfers
    participant AccPort as AccountsHttpClient
    participant Kafka as Apache Kafka

    Cliente->>GW: POST /api/v1/transfers<br/>Header X-Idempotency-Key: abc-123<br/>Body {sourceAccountId, targetAccountId, amount}
    GW->>UC: Forward (AuthenticationFilter ya validó JWT)

    UC->>TrDB: findByIdempotencyKey("abc-123")
    TrDB-->>UC: vacío (no procesada aún)

    UC->>UC: sourceAccountId != targetAccountId ?

    UC->>AccPort: findOwnedAccounts(bearerToken)
    AccPort->>GW: GET /api/v1/accounts (Bearer forward)
    GW-->>AccPort: 200 OK [cuentas del usuario]
    AccPort-->>UC: cuentas propias
    UC->>UC: ¿sourceAccountId está entre las propias? (si no: 403 FORBIDDEN)

    UC->>AccPort: accountExists(bearerToken, targetAccountId)
    AccPort->>GW: GET /api/v1/accounts/{targetId}/exists
    GW-->>AccPort: 200 OK { exists: true }
    AccPort-->>UC: true (si no: 404 NOT_FOUND)

    UC->>UC: source.balance >= amount ? (si no: 422 UNPROCESSABLE_ENTITY)

    UC->>TrDB: INSERT INTO transfers (status=COMPLETED, idempotency_key='abc-123')
    TrDB-->>UC: transfer persistida

    par Publicación de eventos
        UC->>Kafka: publish NotificationEvent (topic notification-events)
    and
        UC->>Kafka: publish TransferAuditEvent (topic transfer-events)
    end

    UC-->>GW: 201 CREATED { id, status: COMPLETED, ... }
    GW-->>Cliente: 201 CREATED
```

> El consumo paralelo de esos dos eventos por `ms-notifications` y por el `AuditListener`
> del monolito está en el diagrama de Saga de
> [`finbank-monolith/README.md`](../../finbank-monolith/README.md#patron-de-consistencia-distribuida).

## 4. Transferencia — Reintento con la misma clave de idempotencia

Cubre los dos caminos por los que `ms-transfers` evita procesar dos veces la misma
operación: la relectura previa (caso normal) y la **condición de carrera** cuando dos
peticiones concurrentes llegan con la misma clave (resuelta por el índice único de
`idempotency_key` en `postgres-transfers`, ver `TransfersUseCase.doExecute`).

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente HTTP
    participant UC as ms-transfers (TransfersUseCase)
    participant TrDB as postgres-transfers

    rect rgb(235, 245, 255)
        Note over Cliente, TrDB: Caso normal — segunda petición llega después de que la primera ya se completó
        Cliente->>UC: POST /api/v1/transfers (X-Idempotency-Key: abc-123)
        UC->>TrDB: findByIdempotencyKey("abc-123")
        TrDB-->>UC: transferencia ya existente
        UC-->>Cliente: 201 CREATED (mismo id y createdAt, sin reprocesar ni republicar eventos)
    end

    rect rgb(255, 245, 235)
        Note over Cliente, TrDB: Condición de carrera — dos peticiones concurrentes, mismo X-Idempotency-Key
        par Petición A
            Cliente->>UC: POST /api/v1/transfers (X-Idempotency-Key: abc-123)
            UC->>TrDB: findByIdempotencyKey -> vacío
            UC->>TrDB: INSERT (gana la carrera)
            TrDB-->>UC: transfer guardada
        and Petición B
            Cliente->>UC: POST /api/v1/transfers (X-Idempotency-Key: abc-123)
            UC->>TrDB: findByIdempotencyKey -> vacío (aún no ve el commit de A)
            UC->>TrDB: INSERT -> DataIntegrityViolationException (constraint único)
            UC->>TrDB: findByIdempotencyKey("abc-123") (recuperación)
            TrDB-->>UC: transfer de A
        end
        UC-->>Cliente: 201 CREATED (A y B reciben el mismo id, un solo INSERT real)
    end
```

## 5. Circuit Breaker + Retry ante caída del monolito

Timing exacto de Resilience4j configurado en `ms-transfers`
([`application.yml`](../../ms-transfers/src/main/resources/application.yml)): 4 intentos
(1 inicial + 3 reintentos) con backoff `1s → 2s → 4s` sobre la llamada de validación de
cuentas, y apertura del circuito al superar 50% de fallos en una ventana de 10 llamadas.
El diagrama de alto nivel de este mismo flujo ya está en
[`finbank-monolith/README.md`](../../finbank-monolith/README.md#diagrama-de-secuencia-happy-path-vs-failure-path);
aquí se detalla la secuencia de reintentos que ese diagrama resume en un solo `loop`.

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente HTTP
    participant UC as ms-transfers (AccountsHttpClient)
    participant CB as Resilience4j (CircuitBreaker + Retry)
    participant Mono as finbank-monolith (caído)

    Note over CB: Circuito en CLOSED, ventana de 10 llamadas, umbral de fallo 50%

    Cliente->>UC: POST /api/v1/transfers
    UC->>CB: findOwnedAccounts(bearerToken)
    CB->>Mono: GET /api/v1/accounts (intento 1)
    Mono--xCB: timeout / connection refused
    CB->>CB: espera backoff 1s
    CB->>Mono: GET /api/v1/accounts (intento 2)
    Mono--xCB: timeout
    CB->>CB: espera backoff 2s
    CB->>Mono: GET /api/v1/accounts (intento 3)
    Mono--xCB: timeout
    CB->>CB: espera backoff 4s
    CB->>Mono: GET /api/v1/accounts (intento 4, último)
    Mono--xCB: timeout
    Note over CB: Los 4 intentos cuentan como UN solo resultado fallido<br/>(circuit-breaker-aspect-order antes de retry-aspect-order)
    CB->>CB: failure-rate-threshold (50%) superado -> circuito pasa a OPEN
    CB-->>UC: excepción (fallback)
    UC-->>Cliente: 503 SERVICE_UNAVAILABLE

    Note over Cliente, Mono: Peticiones siguientes, circuito ya OPEN
    Cliente->>UC: POST /api/v1/transfers
    UC->>CB: findOwnedAccounts(bearerToken)
    CB--xCB: Fail-fast (no llama a Mono), ~17ms
    CB-->>UC: excepción inmediata
    UC-->>Cliente: 503 SERVICE_UNAVAILABLE

    Note over CB: Tras wait-duration-in-open-state (10s),<br/>pasa a HALF_OPEN y permite 3 llamadas de prueba
```

## 6. Consumo de eventos en `ms-notifications` con reintento y descarte

`KafkaConsumerConfig` en `ms-notifications` envuelve el `@KafkaListener` de
`notification-events` con un `DefaultErrorHandler` + `ExponentialBackOff` (1s, 2s, 4s;
máximo 3 reintentos). Si la causa del fallo no se resuelve (p. ej. la base de datos
sigue caída), el mensaje se descarta y el offset avanza — no hay una DLQ configurada en
este incremento, solo el log de auditoría del _recoverer_.

```mermaid
sequenceDiagram
    autonumber
    participant Kafka as Apache Kafka (topic notification-events)
    participant Listener as ms-notifications (@KafkaListener)
    participant ErrH as DefaultErrorHandler (ExponentialBackOff)
    participant DB as postgres-notifications (caída)

    Kafka->>Listener: poll() -> NotificationEvent (partition=0, offset=7)
    Listener->>DB: INSERT INTO notifications
    DB--xListener: SQLException (connection refused)
    Listener->>ErrH: onError(record, exception)

    ErrH->>ErrH: espera backoff 1s
    ErrH->>Listener: reintento 1
    Listener->>DB: INSERT INTO notifications
    DB--xListener: SQLException

    ErrH->>ErrH: espera backoff 2s
    ErrH->>Listener: reintento 2
    Listener->>DB: INSERT INTO notifications
    DB--xListener: SQLException

    ErrH->>ErrH: espera backoff 4s
    ErrH->>Listener: reintento 3 (último, maxAttempts=3)
    Listener->>DB: INSERT INTO notifications
    DB--xListener: SQLException

    ErrH->>ErrH: log.error("Giving up on notification event after 3 retries: partition=0 offset=7")
    ErrH->>Kafka: commit offset 7 (se descarta el mensaje, el consumo continúa)
```

## 7. Consulta de notificaciones y auditoría (lectura)

Ambas rutas son de solo lectura, filtradas siempre por el usuario autenticado
(`X-User-Id` inyectado por el Gateway) — ningún endpoint permite leer datos de otro
usuario.

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente HTTP
    participant GW as API Gateway
    participant Notif as ms-notifications
    participant NotifDB as postgres-notifications
    participant Audit as finbank-monolith (Audit)
    participant MonoDB as postgres-monolith (audit.*)

    Cliente->>GW: GET /api/v1/notifications (Authorization: Bearer <token>)
    GW->>Notif: GET /api/v1/notifications (X-User-Id)
    Notif->>NotifDB: SELECT notifications WHERE user_id = X-User-Id ORDER BY created_at DESC
    NotifDB-->>Notif: filas
    Notif-->>GW: 200 OK [ {type, payload, occurredAt}, ... ]
    GW-->>Cliente: 200 OK

    Cliente->>GW: GET /api/v1/audit (Authorization: Bearer <token>)
    GW->>Audit: GET /audit (StripPrefix=2, X-User-Id)
    Audit->>MonoDB: SELECT audit_entries WHERE user_id = X-User-Id
    MonoDB-->>Audit: filas
    Audit-->>GW: 200 OK [ {transferId, amount, occurredAt}, ... ]
    GW-->>Cliente: 200 OK
```
