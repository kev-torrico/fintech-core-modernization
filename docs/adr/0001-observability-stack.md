# ADR-0001: Stack de Observabilidad (Tracing, Logs, Métricas)

**Estado:** Aceptado
**Contexto del sistema:** api-gateway (WebFlux) + finbank-monolith, ms-notifications, ms-transfers (Spring MVC) — Java 17 / Spring Boot 3.2.5, comunicación HTTP síncrona (vía api-gateway) y asíncrona (Kafka).

## Decisión

| Pilar | Tecnología | Motivo |
|---|---|---|
| Trazas distribuidas | **Micrometer Tracing** (bridge **Brave**) + **Zipkin** como backend | API de instrumentación oficial de Spring Boot 3.x (reemplaza a Sleuth). Brave es el bridge más liviano y mejor documentado para Zipkin; evita levantar un OpenTelemetry Collector solo para este alcance. Zipkin corre en un único contenedor, sin dependencias externas. |
| Propagación de contexto | **W3C TraceContext** (`traceparent`/`tracestate`) vía `management.tracing.propagation.type=W3C` | Estándar interoperable (no propietario como B3); Brave lo soporta nativamente desde Spring Boot 3.x sin cambiar de bridge. |
| Logs estructurados | **Logback + `logstash-logback-encoder`** (JSON) | Ya es el logger por defecto de Spring Boot; el encoder de Logstash solo cambia el formato de salida a JSON, sin tocar la API de logging (`Slf4j`/`@Slf4j`). Micrometer Tracing puebla automáticamente el MDC (`traceId`, `spanId`); el encoder los serializa como campos de primer nivel sin configuración adicional. |
| Métricas | **Micrometer + Prometheus (`/actuator/prometheus`) + Grafana** | Actuator ya expone métricas (`http.server.requests`, JVM, Kafka); el registry de Prometheus solo cambia el formato de exposición. Prometheus hace *pull* scraping — no requiere agentes ni sidecars adicionales. Grafana es el visualizador estándar de facto para Prometheus. |
| Consumer lag de Kafka | **`kafka-exporter`** (contenedor independiente) | El lag "real" vive en los offsets del broker, no en el cliente. Un exporter dedicado que lee directamente del broker es más fiable que métricas JMX del lado del consumidor y no acopla la métrica al ciclo de vida de cada microservicio. |

## Alternativas consideradas

- **OpenTelemetry SDK/Collector + Tempo + Loki (stack "LGTM")**: más completo y es el estándar hacia el que converge el ecosistema, pero introduce un componente adicional (el Collector) y una curva de configuración mayor (procesadores, exporters, pipelines) que no se justifica para 4 servicios. Se deja como evolución natural: Micrometer Tracing permite cambiar el *bridge* de Brave a OTel (`micrometer-tracing-bridge-otel`) sin tocar el código de negocio, solo dependencias y `application.yml`.
- **Jaeger en vez de Zipkin**: equivalente en esfuerzo; se eligió Zipkin porque el *reporter* de Brave habla su protocolo nativamente (`zipkin-reporter-brave`), sin adaptadores.
- **ELK (Elasticsearch + Logstash + Kibana) para logs**: viable, pero agrega un componente pesado (Elasticsearch) solo para este incremento. Se opta por *stdout* en JSON — cualquier recolector (Promtail, Fluent Bit, Filebeat) puede engancharse después sin cambiar el código de las apps, ya que el contrato es "logs JSON en stdout".

## Consecuencias

- Todas las apps quedan acopladas a `io.micrometer:micrometer-tracing-bridge-brave`, `io.zipkin.reporter2:zipkin-reporter-brave`, `io.micrometer:micrometer-registry-prometheus` y `net.logstash.logback:logstash-logback-encoder` — las tres primeras gestionadas por el BOM de Spring Boot (sin fijar versión); la última requiere versión explícita.
- El *sampling* de trazas se deja en `1.0` (100%) para este entorno de desarrollo/demo. En producción debe bajarse (p. ej. `0.1`) para no saturar Zipkin ni el broker.
- La propagación hacia Kafka se resuelve **declarativamente** (`spring.kafka.template.observation-enabled` / `spring.kafka.listener.observation-enabled`), sin tocar manualmente `ProducerRecord`/`ConsumerRecord` — Spring for Apache Kafka 3.1+ ya sabe inyectar/leer el header `traceparent` cuando la instrumentación de Observation está activa.
- El api-gateway es reactivo (WebFlux); para que el MDC (traceId/spanId/userId) sobreviva el salto entre hilos de Reactor se habilita `spring.reactor.context-propagation=auto` (Boot 3.1+). Es la única app que necesita esta configuración adicional.
