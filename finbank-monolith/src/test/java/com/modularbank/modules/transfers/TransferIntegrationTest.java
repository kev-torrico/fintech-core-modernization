package com.modularbank.modules.transfers;

import com.auth0.jwt.JWT;
import com.modularbank.shared.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class TransferIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenA;
    private String tokenB;
    private String accountAId;
    private String accountBId;

    /**
     * Fuera del api-gateway el Monolito ya no decodifica JWT: la identidad llega en
     * X-User-Id. Estos tests simulan lo que el AuthenticationFilter del gateway hace
     * en producción, decodificando localmente el subject del token emitido.
     */
    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-User-Id", JWT.decode(token).getSubject());
        return headers;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String emailA = "alice-" + suffix + "@example.com";
        String emailB = "bob-" + suffix + "@example.com";

        rest.postForEntity("/auth/register",
            Map.of("email", emailA, "password", "Password123!", "name", "Alice"), Map.class);
        rest.postForEntity("/auth/register",
            Map.of("email", emailB, "password", "Password123!", "name", "Bob"), Map.class);

        tokenA = (String) rest.postForEntity("/auth/login",
            Map.of("email", emailA, "password", "Password123!"), Map.class)
            .getBody().get("accessToken");
        tokenB = (String) rest.postForEntity("/auth/login",
            Map.of("email", emailB, "password", "Password123!"), Map.class)
            .getBody().get("accessToken");

        HttpHeaders headersA = authHeaders(tokenA);
        accountAId = (String) rest.exchange("/accounts", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headersA), Map.class).getBody().get("id");

        HttpHeaders headersB = authHeaders(tokenB);
        accountBId = (String) rest.exchange("/accounts", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headersB), Map.class).getBody().get("id");
    }

    @Test
    void transferBetweenAccountsInsufficientFunds() {
        HttpHeaders headers = authHeaders(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", accountAId,
                "targetAccountId", accountBId,
                "amount", "100.00"
            ), headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void selfTransferReturns422() {
        HttpHeaders headers = authHeaders(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", accountAId,
                "targetAccountId", accountAId,
                "amount", "10.00"
            ), headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void transferFromAnotherUsersAccountReturns403() {
        HttpHeaders headers = authHeaders(tokenB);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", accountAId,
                "targetAccountId", accountBId,
                "amount", "1.00"
            ), headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void historyForOtherUsersAccountReturns403() {
        HttpHeaders headers = authHeaders(tokenA);

        ResponseEntity<Map> response = rest.exchange(
            "/transfers?accountId=" + accountBId,
            HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void successfulTransferCreatesHistoryEntry() {
        jdbc.update("UPDATE accounts.accounts SET balance = ? WHERE id = ?::uuid",
            new BigDecimal("1000.0000"), accountAId);

        HttpHeaders headers = authHeaders(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> transfer = rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", accountAId,
                "targetAccountId", accountBId,
                "amount", "250.00",
                "reference", "test-payment"
            ), headers), Map.class);
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transfer.getBody()).containsKey("id");

        ResponseEntity<List> history = rest.exchange(
            "/transfers?accountId=" + accountAId,
            HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).hasSize(1);
    }

    @Test
    void auditRecordsTransferEvent() {
        jdbc.update("UPDATE accounts.accounts SET balance = ? WHERE id = ?::uuid",
            new BigDecimal("500.0000"), accountAId);

        HttpHeaders headers = authHeaders(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", accountAId,
                "targetAccountId", accountBId,
                "amount", "50.00"
            ), headers), Map.class);

        ResponseEntity<List> audit = rest.exchange(
            "/audit", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(audit.getBody()).isNotEmpty();
    }

    @Test
    void notificationsRecordTransferEvent() {
        jdbc.update("UPDATE accounts.accounts SET balance = ? WHERE id = ?::uuid",
            new BigDecimal("500.0000"), accountAId);

        HttpHeaders headers = authHeaders(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", accountAId,
                "targetAccountId", accountBId,
                "amount", "75.00"
            ), headers), Map.class);

        ResponseEntity<List> notifications = rest.exchange(
            "/notifications", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(notifications.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notifications.getBody()).isNotEmpty();
    }
}
