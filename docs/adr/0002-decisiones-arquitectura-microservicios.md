# ADR-001 a ADR-011 — Decisiones de arquitectura de la migración a microservicios

**Estado general:** Aprobado
**Contexto del sistema:** migración progresiva del monolito modular FinBank (Auth,
Accounts, Transfers, Notifications, Audit) hacia microservicios mediante Strangler Fig,
descrita en el [README de la raíz](../../README.md). Este documento reúne las decisiones
de arquitectura tomadas durante las 5 fases del reto (extracción del primer módulo,
segunda extracción y comunicación asíncrona, resiliencia y contratos de eventos,
observabilidad, y el análisis de trade-offs final).

> El stack de observabilidad (ADR-009 en este documento) tiene su propio ADR técnico,
> más detallado, en [`0001-observability-stack.md`](0001-observability-stack.md).

---

<a id="adr-001"></a>

## ADR-001 — Elección del primer módulo a extraer: microservicio de Notificaciones (`ms-notifications`)

**Estado:** Aprobado

**Contexto:** el monolito de FinBank requería iniciar su desacoplamiento hacia una
arquitectura de microservicios. Se necesitaba identificar un módulo de bajo riesgo y
desacoplado del dominio financiero central para validar el patrón de migración y la
infraestructura de comunicación asíncrona.

**Opciones evaluadas:**

- **Módulo de Cuentas / Monedero** — Ventaja: central a la lógica. Desventaja: alto
  riesgo, acoplamiento transaccional severo y gran complejidad para el primer corte.
- **Módulo de Notificaciones** — Ventaja: lógica de lectura/escritura periférica, no
  bloqueante, tolerancia a fallos alta y naturaleza naturalmente asíncrona. Desventaja:
  requiere la introducción inmediata de un message broker.

**Decisión:** extraer Notificaciones como el primer microservicio (`ms-notifications`).
Esto permitió establecer las bases de la arquitectura orientada a eventos (EDA) sin
arriesgar la consistencia transaccional del núcleo bancario.

**Consecuencias:**

- *Positivas:* menor riesgo operativo en la primera fase; independización de la
  escalabilidad de envío de correos/alertas; creación del pipeline de eventos reutilizable.
- *Negativas / Deuda:* necesidad de gestionar una base de datos propia para auditoría de
  notificaciones desde el día uno.

---

<a id="adr-002"></a>

## ADR-002 — Elección del segundo módulo a extraer: microservicio de Transferencias (`ms-transfers`)

**Estado:** Aprobado

**Contexto:** tras validar la infraestructura de eventos con el primer microservicio, era
necesario desacoplar la funcionalidad de negocio con mayor tasa de cambio y demanda de
escalabilidad horizontal: el procesamiento de transferencias monetarias.

**Opciones evaluadas:**

- **Módulo de Autenticación / Usuarios** — Ventaja: aísla la seguridad. Desventaja: alto
  impacto transversal en todos los endpoints, sin agregar valor al core transaccional.
- **Módulo de Transferencias** — Ventaja: alta carga transaccional, requerimientos de
  idempotencia y necesidad de coordinar llamadas HTTP/asíncronas con otros dominios.
  Desventaja: rompe la transacción ACID directa sobre las cuentas.

**Decisión:** extraer Transferencias como el segundo microservicio (`ms-transfers`).
Permite escalar las transacciones financieras de forma independiente del monolito.

**Consecuencias:**

- *Positivas:* escalamiento independiente de la lógica de transferencias; aislamiento de
  fallos durante picos de operación.
- *Negativas / Deuda:* introducción de consistencia eventual al comunicarse con el
  monolito (`finbank-monolith`) y procesamiento asíncrono para eventos posteriores.

---

<a id="adr-003"></a>

## ADR-003 — API Gateway / reverse proxy elegido: Spring Cloud Gateway

**Estado:** Aprobado

**Contexto:** con la coexistencia del monolito y múltiples microservicios, los clientes
externos requerían un punto de entrada único (*single point of entry*) que manejara el
enrutamiento, la seguridad JWT y la abstracción de URLs internas.

**Opciones evaluadas:**

- **NGINX / Envoy** — Ventaja: altísimo rendimiento a nivel de red. Desventaja: mayor
  curva de configuración y desacoplado del ecosistema Java/Spring Boot.
- **Spring Cloud Gateway** — Ventaja: integración nativa con Spring Security, filtros
  reactivos, fácil configuración en Java/YAML y propagación automática de contexto para
  observabilidad (Micrometer). Desventaja: mayor consumo de memoria que proxies en C/C++.

**Decisión:** Spring Cloud Gateway ejecutándose en el puerto 8080 como punto de entrada
unificado y validador de tokens JWT.

**Consecuencias:**

- *Positivas:* centralización de la seguridad (JWT) y ruteo dinámico; instrumentación de
  trazas desde el borde (`api-gateway`).
- *Negativas / Deuda:* constituye un punto único de fallo (SPOF) si no se despliega en
  alta disponibilidad.

---

<a id="adr-004"></a>

## ADR-004 — Message broker elegido: Apache Kafka

**Estado:** Aprobado

**Contexto:** la comunicación entre `ms-transfers`, `finbank-monolith` y
`ms-notifications` requería un mecanismo de mensajería asíncrono, persistente, de alto
rendimiento y con soporte para republicación de eventos.

**Opciones evaluadas:**

- **RabbitMQ** — Ventaja: enrutamiento complejo de mensajes amigable. Desventaja: menor
  capacidad de retención masiva y rendimiento bajo cargas extremas de eventos en
  comparación con un log append-only.
- **Apache Kafka** — Ventaja: persistencia basada en commit-log, alto throughput, orden
  garantizado por partición y soporte nativo para escalamiento horizontal con consumer
  groups. Desventaja: requiere coordinación con Zookeeper/KRaft y mayor complejidad de
  configuración inicial.

**Decisión:** elegir Apache Kafka (vía Confluent `cp-kafka:7.5.0`) con la métrica de
propagación de contexto de Micrometer activada.

**Consecuencias:**

- *Positivas:* replay de eventos, desacoplamiento temporal de microservicios y soporte
  para auditoría pasiva.
- *Negativas / Deuda:* complejidad adicional para mantener la sincronización y la
  necesidad de manejar el consumer group lag.

---

<a id="adr-005"></a>

## ADR-005 — Base de datos del primer microservicio (`ms-notifications`): PostgreSQL 15 (aislada)

**Estado:** Aprobado

**Contexto:** siguiendo el patrón Database-per-Service, el microservicio de
notificaciones requería su propio almacén de datos aislado para persistir plantillas,
logs de envío y estados de notificaciones.

**Opciones evaluadas:**

- **MongoDB (NoSQL)** — Ventaja: esquema flexible para contenido de notificaciones.
  Desventaja: aumenta la heterogeneidad del stack tecnológico del equipo.
- **PostgreSQL 15 (Alpine)** — Ventaja: consistencia con el stack global, bajo consumo de
  recursos (imagen Alpine), soporte relacional robusto y ejecutable en contenedor
  independiente (`postgres-notifications` en el puerto 5433). Desventaja: requiere
  definir esquemas estructurados de migración.

**Decisión:** seleccionar PostgreSQL 15 Alpine exclusivo para `ms-notifications`.

**Consecuencias:**

- *Positivas:* aislamiento de datos total; administración uniforme con el resto del stack.
- *Negativas / Deuda:* aumento en la cantidad de contenedores y recursos de base de datos
  a monitorear.

---

<a id="adr-006"></a>

## ADR-006 — Base de datos del segundo microservicio (`ms-transfers`): PostgreSQL 15 (aislada)

**Estado:** Aprobado

**Contexto:** el microservicio de transferencias debía almacenar sus propias
transacciones, estados de procesamiento (`PENDING`, `COMPLETED`, `FAILED`) y claves de
idempotencia sin depender del esquema del monolito.

**Opciones evaluadas:**

- **Compartir la base de datos del monolito (`modular_bank`)** — Ventaja: facilidad de
  consultas SQL directas. Desventaja: viola el principio de acoplamiento de base de
  datos y bloquea la independencia de despliegue.
- **PostgreSQL 15 (Alpine) exclusiva** — Ventaja: aislamiento estricto de dominio
  (`finbank_transfers_db` en el puerto 5434), capacidad de evolucionar el esquema de
  transferencias independientemente. Desventaja: requiere llamadas HTTP/eventos para
  validar entidades de otros dominios.

**Decisión:** adoptar PostgreSQL 15 Alpine dedicada exclusivamente a `ms-transfers`.

**Consecuencias:**

- *Positivas:* autonomía de despliegue y migración mediante Flyway; garantía de fronteras
  de contexto (*bounded context*).
- *Negativas / Deuda:* imposibilidad de hacer `JOIN`s SQL con la tabla de
  cuentas/usuarios del monolito.

---

<a id="adr-007"></a>

## ADR-007 — Patrón de consistencia distribuida: Saga basada en coreografía con idempotencia

**Estado:** Aprobado

**Contexto:** al separar las transferencias del monolito, ya no es posible ejecutar una
única transacción ACID (`@Transactional`) entre la validación/débito de cuenta, el
registro de la transferencia y la notificación.

**Opciones evaluadas:**

- **Two-Phase Commit (2PC) / XA Transactions** — Ventaja: consistencia fuerte.
  Desventaja: pésimo rendimiento, bloqueos de red y antipatrón en microservicios.
- **Saga basada en coreografía** — Ventaja: descentralizada, asíncrona, reactiva y
  altamente escalable mediante eventos de Kafka (`transfer-events`,
  `notification-events`). Desventaja: requiere manejar consistencia eventual y diseño de
  idempotencia (`X-Idempotency-Key`).

**Decisión:** implementar Saga coreografiada basada en eventos combinada con cabeceras
de idempotencia en las peticiones HTTP y en los consumidores de Kafka.

**Consecuencias:**

- *Positivas:* sistema altamente tolerante a fallos y sin bloqueos síncronos de larga
  duración.
- *Negativas / Deuda:* la consistencia es eventual; requiere lógica explícita para evitar
  el doble procesamiento de eventos.

---

<a id="adr-008"></a>

## ADR-008 — Arquitectura interna de cada microservicio: arquitectura en capas guiada por dominio (DDD)

**Estado:** Aprobado

**Contexto:** se debía establecer una estructura interna estandarizada para
`ms-transfers`, `ms-notifications` y `finbank-monolith` que separase las
responsabilidades de infraestructura, aplicación y dominio.

**Opciones evaluadas:**

- **Arquitectura hexagonal (puertos y adaptadores) estricta** — Ventaja: máxima
  abstracción del framework. Desventaja: exceso de código boilerplate y mapeadores para
  servicios de tamaño mediano.
- **Arquitectura por capas limpia (`Controller → Service → Repository / EventPublisher`)**
  — Ventaja: balance ideal entre simplicidad, mantenibilidad en Spring Boot, separación
  de DTOs/Entidades y rapidez de desarrollo.

**Decisión:** adoptar arquitectura por capas basada en principios DDD, estructurada en
paquetes `controller`, `service`, `repository`, `model`/`entity`, `config` y `dto`.

**Consecuencias:**

- *Positivas:* curva de aprendizaje homogénea para el equipo; código mantenible y
  testeable.
- *Negativas / Deuda:* ligero acoplamiento de las clases de servicio a las anotaciones
  de Spring.

---

<a id="adr-009"></a>

## ADR-009 — Stack de observabilidad: Micrometer Tracing (Brave), Zipkin, Prometheus y Grafana

**Estado:** Aprobado

**Contexto:** se requería visibilidad completa del ciclo de vida de los requests a
través de llamadas HTTP y comunicación asíncrona mediante Kafka.

**Opciones evaluadas:**

- **Spring Cloud Sleuth** — Ventaja: históricamente estándar en Spring Boot 2.x.
  Desventaja: deprecado en Spring Boot 3.x.
- **Micrometer Tracing (Brave Bridge) + Zipkin + Prometheus + Grafana** — Ventaja:
  estándar moderno de Spring Boot 3.x; soporte para contexto de traza W3C
  (`traceparent`); interoperabilidad nativa entre HTTP y Kafka. Desventaja: requiere
  configuración explícita de `ObservationRegistry` y `ContainerCustomizer` en listeners
  de Kafka en Spring Boot 3.2+.

**Decisión:** implementar Micrometer Tracing con Brave, exportando trazas a Zipkin
(puerto 9411), métricas de scraping a Prometheus (puerto 9090) y tableros en Grafana
(puerto 3000).

**Consecuencias:**

- *Positivas:* trazabilidad distribuida end-to-end con propagación de `traceId` en HTTP y
  cabeceras de Kafka.
- *Negativas / Deuda:* costo de CPU/red para el envío de spans y recolección de métricas.

> El detalle técnico de esta decisión (alternativas descartadas con más profundidad,
> configuración de propagación de contexto en WebFlux, formato de logs) está en
> [`0001-observability-stack.md`](0001-observability-stack.md).

---

<a id="adr-010"></a>

## ADR-010 — Contrato de eventos: formato JSON plano por DTO y versionamiento por ruta/topic

**Estado:** Aprobado

**Contexto:** se requería definir el formato de serialización y la estrategia de
versionamiento para los eventos transmitidos mediante Apache Kafka entre `ms-transfers`,
`finbank-monolith` y `ms-notifications`.

**Opciones evaluadas:**

- **Event envelope genérico (wrapper con versión implícita en el JSON)** — Ventaja:
  metadatos globales unificados en el body. Desventaja: agrega complejidad innecesaria en
  la deserialización de Jackson y boilerplate extra en los microservicios.
- **JSON plano basado en DTOs con versionamiento en rutas/topics** — Ventaja: mapeo
  directo y transparente con las clases Java del dominio (`TransferEvent`,
  `NotificationEvent`), máxima velocidad de desarrollo, integración nativa con Spring
  Kafka + Jackson serializer, y versionamiento explícito a nivel de endpoints/canales
  (`/api/v1/...`). Desventaja: los metadatos de contexto (tipo, timestamp) dependen del
  payload del DTO o de las cabeceras del mensaje.

**Decisión:** adoptar JSON plano basado en DTOs fuertemente tipados, versionando la API a
nivel de ruta (`v1`) y utilizando serializadores de Jackson
(`JsonSerializer`/`JsonDeserializer`) para transmitir el payload directo en el cuerpo del
mensaje de Kafka.

**Consecuencias:**

- *Positivas:* código limpio, sin sobrecarga de wrappers; integración directa y sin
  fricción con los modelos del dominio de Spring Boot.
- *Negativas / Deuda:* si cambia la estructura del DTO de forma destructiva, requiere
  crear un nuevo topic (p. ej. `transfer-events-v2`) para no romper consumidores antiguos.

---

<a id="adr-011"></a>

## ADR-011 — Estrategia de migración de esquemas sin downtime: Flyway y Expand-and-Contract

**Estado:** Aprobado

**Contexto:** las evoluciones de bases de datos relacionales en `postgres-monolith`,
`postgres-transfers` y `postgres-notifications` debían ejecutarse de forma automatizada
al iniciar las aplicaciones, evitando bloqueos e incompatibilidades de esquemas.

**Opciones evaluadas:**

- **Scripts SQL manuales / DDL auto de Hibernate (`spring.jpa.hibernate.ddl-auto=update`)**
  — Ventaja: cero configuración. Desventaja: inseguro en producción, propenso a
  corrupción de datos y sin control de versiones.
- **Flyway con patrón Expand-and-Contract** — Ventaja: control de versiones de SQL
  declarativo e inmutable (`V1__...sql`), migraciones ejecutadas automáticamente durante
  el startup de Spring Boot y soporte para cambios de esquema no destructivos.

**Decisión:** utilizar Flyway (`SPRING_FLYWAY_ENABLED=true`) en combinación con la
estrategia de diseño de base de datos Expand-and-Contract (añadir columnas/tablas nuevas
sin borrar de inmediato las anteriores).

**Consecuencias:**

- *Positivas:* trazabilidad exacta de las versiones de la base de datos; despliegues
  repetibles e idempotentes.
- *Negativas / Deuda:* requiere disciplina estricta al escribir los scripts SQL en el
  directorio `db/migration`.

---

<a id="trade-offs"></a>

## Análisis de trade-offs

**I. ¿La consistencia eventual introducida por la comunicación asíncrona es aceptable
para todas las operaciones bancarias de FinBank, o existen operaciones donde no debería
aplicarse?**

No, en aquellas operaciones financieras críticas donde el saldo o la identidad de la
cuenta se ven afectados.

**II. ¿En qué operaciones fue necesario sacrificar disponibilidad para mantener
consistencia?**

En las transferencias: al ejecutarse una en `ms-transfers`, las consultas de validación
de cuenta (`GET /accounts` y `GET /accounts/{id}/exists`) se realizan de forma síncrona
por HTTP contra el monolito. Si la cuenta de origen no tiene saldo suficiente o el
monolito no responde, la transferencia se rechaza de inmediato en lugar de aceptar la
solicitud a ciegas.

**III. ¿Qué tan difícil sería revertir alguna de las decisiones tomadas? ¿Cuáles son
reversibles y cuáles no?**

Se puede cambiar de Spring Cloud Gateway a NGINX, sustituir PostgreSQL por otra base
relacional o ajustar el formato JSON con relativa facilidad. Sin embargo, el patrón
Database-per-Service y la migración a Saga son irreversibles en la práctica: fusionar de
nuevo las bases de datos en el monolito requeriría mucho tiempo y esfuerzo para
reestructurar llaves foráneas y unificar transacciones al migrar los datos.

**IV. ¿El incremento en complejidad operativa está justificado para el tamaño y carga
actuales de FinBank?**

El volumen actual de transacciones, al ser un proyecto MVP a pequeña escala, no
justifica la complejidad de gestionar Apache Kafka, Zipkin, Prometheus, Grafana y
múltiples bases de datos, que además consumen bastantes recursos de procesamiento. Sin
embargo, si el proyecto fuese empresarial —con vista a escalabilidad, aislamiento de
fallos, observabilidad y entrega continua— la arquitectura propuesta sí estaría
justificada.

**V. ¿Hubo módulos que se consideró no extraer? ¿Por qué?**

No se consideró extraer el módulo de Cuentas, la gestión de usuarios ni el de Auditoría,
porque constituyen el core del sistema bancario y hubiese sido muy complejo extraerlos en
fases tan tempranas del desarrollo. Mantenerlos en el monolito permitió la extracción
segura de `ms-notifications` y `ms-transfers`. Se recalca que `ms-transfers` utiliza al
monolito como fuente única de verdad, con peticiones síncronas hacia el módulo de
cuentas.
