package com.finbank.transfers.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Espejo del AccountSummary que expone el Monolito en GET /api/v1/accounts. No se
 * comparte el .jar entre servicios: solo debe coincidir la forma del JSON.
 */
public record AccountSummary(UUID id, String accountNumber, BigDecimal balance) {
}
