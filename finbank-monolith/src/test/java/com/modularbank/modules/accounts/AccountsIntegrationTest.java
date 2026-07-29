package com.modularbank.modules.accounts;

import com.modularbank.shared.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AccountsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String registerAndLogin(String email) {
        rest.postForEntity("/auth/register",
            Map.of("email", email, "password", "Password123!", "name", "User"), Map.class);
        return (String) rest.postForEntity("/auth/login",
            Map.of("email", email, "password", "Password123!"), Map.class)
            .getBody().get("accessToken");
    }

    @Test
    void createAccountAndGetBalance() {
        String token = registerAndLogin("acc@example.com");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> createResponse = rest.exchange(
            "/accounts", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headers), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String accountId = (String) createResponse.getBody().get("id");
        ResponseEntity<Map> balanceResponse = rest.exchange(
            "/accounts/" + accountId + "/balance", HttpMethod.GET,
            new HttpEntity<>(headers), Map.class);
        assertThat(balanceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balanceResponse.getBody().get("amount")).isEqualTo("0.0000");
    }

    @Test
    void listAccountsReturnsOwnedAccounts() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String token = registerAndLogin("list-" + suffix + "@example.com");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        rest.exchange("/accounts", HttpMethod.POST, new HttpEntity<>(Map.of(), headers), Map.class);
        rest.exchange("/accounts", HttpMethod.POST, new HttpEntity<>(Map.of(), headers), Map.class);

        ResponseEntity<List> response = rest.exchange(
            "/accounts", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }
}
