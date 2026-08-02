package com.finbank.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
/**
 * Fuente única de la documentación pública de FinBank. El Gateway no reenvía ni
 * agrega los /v3/api-docs del Monolito o de ms-notifications (esos servicios ya no
 * exponen springdoc en absoluto): la especificación completa se construye aquí, a
 * mano, describiendo exactamente la superficie que el Gateway expone en /api/v1/**.
 * Añadir un endpoint proxied nuevo implica añadir su PathItem en {@link #unifiedPathsCustomizer()}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("FinBank API")
                .description("API pública unificada de FinBank, servida a través del api-gateway. "
                    + "Agrupa el Core/Auth (Monolito) y el microservicio de Notificaciones detrás de "
                    + "un único punto de entrada JWT-protegido.")
                .version("v1")
                .contact(new Contact().name("FinBank Platform Team")))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Token obtenido en POST /api/v1/auth/login o /api/v1/auth/register. "
                        + "El api-gateway lo valida y propaga la identidad downstream; los servicios "
                        + "internos ya no lo decodifican.")))
            // Requisito global: toda operación exige Bearer salvo que declare security([]) explícitamente.
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public OpenApiCustomizer unifiedPathsCustomizer() {
        return openApi -> {
            Paths paths = new Paths();
            paths.addPathItem("/api/v1/auth/register", authRegisterPath());
            paths.addPathItem("/api/v1/auth/login", authLoginPath());
            paths.addPathItem("/api/v1/auth/refresh", authRefreshPath());
            paths.addPathItem("/api/v1/accounts", accountsPath());
            paths.addPathItem("/api/v1/accounts/{accountId}/balance", accountBalancePath());
            paths.addPathItem("/api/v1/transfers", transfersPath());
            paths.addPathItem("/api/v1/audit", auditPath());
            paths.addPathItem("/api/v1/notifications", notificationsPath());
            openApi.setPaths(paths);
        };
    }

    // ---------------------------------------------------------------------
    // Auth (Monolito) — públicos, sin Bearer
    // ---------------------------------------------------------------------

    private PathItem authRegisterPath() {
        return new PathItem().post(publicOperation(
            "Registrar un nuevo usuario", "Auth",
            jsonBody(Map.of("email", "user@example.com", "password", "Password123!", "name", "Jane Doe")),
            responses(
                apiResponse("201", "Usuario creado", authTokenExample()),
                apiResponse("409", "El email ya está registrado", null)
            )));
    }

    private PathItem authLoginPath() {
        return new PathItem().post(publicOperation(
            "Iniciar sesión", "Auth",
            jsonBody(Map.of("email", "user@example.com", "password", "Password123!")),
            responses(
                apiResponse("200", "Login exitoso", authTokenExample()),
                apiResponse("401", "Credenciales inválidas", null)
            )));
    }

    private PathItem authRefreshPath() {
        return new PathItem().post(publicOperation(
            "Renovar el access token", "Auth",
            jsonBody(Map.of("refreshToken", "eyJhbGciOi...")),
            responses(
                apiResponse("200", "Token renovado (refresh token rotado)", authTokenExample()),
                apiResponse("401", "Refresh token inválido o expirado", null)
            )));
    }
        // ---------------------------------------------------------------------
        // Transferencias (ms-transfers) — protegido, exige X-Idempotency-Key
        // ---------------------------------------------------------------------

        private PathItem transfersPath() {
            return new PathItem()
                .post(securedOperation(
                    "Crear una nueva transferencia entre cuentas", 
                    "Transfers",
                    jsonBody(Map.of(
                        "sourceAccountId", "b7a54a2a-718e-4a60-9b37-123456789abc",
                        "targetAccountId", "c8b65b3b-829f-5b71-ac48-987654321def",
                        "amount", 150.00
                    )),
                    responses(
                        apiResponse("201", "Transferencia procesada exitosamente", jsonExample(Map.of(
                            "id", "t9z88111-2222-3333-4444-555555555555",
                            "status", "COMPLETED",
                            "amount", 150.00
                        ))),
                        apiResponse("400", "Petición inválida o saldo insuficiente", null),
                        apiResponse("503", "Servicio no disponible (Circuit Breaker activo)", null)
                    )
                ).addParametersItem(new Parameter()
                    .in("header")
                    .name("X-Idempotency-Key")
                    .description("Clave única opcional para garantizar idempotencia en la transacción (ej. UUID v4)")
                    .required(false)
                    .schema(new Schema<String>().type("string").example("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"))
                ))
                .get(securedOperation(
                    "Listar las transferencias del usuario autenticado", 
                    "Transfers",
                    null,
                    responses(apiResponse("200", "Historial de transferencias", null))
                ));
        }
    // ---------------------------------------------------------------------
    // Core del banco (Monolito) — protegidos, heredan el Bearer global
    // ---------------------------------------------------------------------

    private PathItem accountsPath() {
        return new PathItem()
            .post(securedOperation("Crear una cuenta para el usuario autenticado", "Accounts",
                null,
                responses(apiResponse("201", "Cuenta creada", jsonExample(Map.of(
                    "id", "b7a5...", "balance", "0.0000"))))))
            .get(securedOperation("Listar las cuentas del usuario autenticado", "Accounts",
                null,
                responses(apiResponse("200", "Cuentas del usuario", null))));
    }

    private PathItem accountBalancePath() {
        return new PathItem().get(securedOperation(
            "Consultar el saldo de una cuenta propia", "Accounts",
            null,
            responses(
                apiResponse("200", "Saldo actual", jsonExample(Map.of("amount", "1000.0000"))),
                apiResponse("403", "La cuenta no pertenece al usuario autenticado", null)
            )));
    }

    private PathItem auditPath() {
        return new PathItem().get(securedOperation(
            "Consultar la bitácora de auditoría del usuario autenticado", "Audit",
            null,
            responses(apiResponse("200", "Eventos de auditoría", null))));
    }

    // ---------------------------------------------------------------------
    // Notificaciones (ms-notifications) — protegido, hereda el Bearer global
    // ---------------------------------------------------------------------

    private PathItem notificationsPath() {
        return new PathItem().get(securedOperation(
            "Listar las notificaciones del usuario autenticado", "Notifications",
            null,
            responses(apiResponse("200", "Notificaciones del usuario", null))));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Operation publicOperation(String summary, String tag, RequestBody body, ApiResponses responses) {
        Operation operation = new Operation()
            .tags(List.of(tag))
            .summary(summary)
            .responses(responses)
            // Override del requisito global: estos 3 endpoints no exigen Bearer.
            .security(Collections.emptyList());
        if (body != null) {
            operation.requestBody(body);
        }
        return operation;
    }

    private Operation securedOperation(String summary, String tag, RequestBody body, ApiResponses responses) {
        Operation operation = new Operation()
            .tags(List.of(tag))
            .summary(summary)
            .responses(responses);
        if (body != null) {
            operation.requestBody(body);
        }
        return operation;
    }

    private RequestBody jsonBody(Map<String, Object> example) {
        return new RequestBody()
            .required(true)
            .content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().example(example))));
    }

    private ApiResponses responses(StatusResponse... entries) {
        ApiResponses apiResponses = new ApiResponses();
        for (StatusResponse entry : entries) {
            apiResponses.addApiResponse(entry.status, entry.response);
        }
        return apiResponses;
    }

    private StatusResponse apiResponse(String status, String description, Content content) {
        ApiResponse response = new ApiResponse().description(description);
        if (content != null) {
            response.content(content);
        }
        return new StatusResponse(status, response);
    }

    private record StatusResponse(String status, ApiResponse response) {
    }

    private Content jsonExample(Map<String, Object> example) {
        return new Content().addMediaType("application/json",
            new MediaType().schema(new Schema<>().example(example)));
    }

    private Content authTokenExample() {
        return jsonExample(Map.of(
            "accessToken", "eyJhbGciOiJIUzI1NiJ9...",
            "refreshToken", "8f6a1c2e..."
        ));
    }
}
