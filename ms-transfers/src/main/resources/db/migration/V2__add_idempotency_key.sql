ALTER TABLE transfers ADD COLUMN idempotency_key VARCHAR(255);

-- Índice único parcial: permite múltiples NULL (peticiones sin header X-Idempotency-Key)
-- pero exige unicidad cuando la clave viene informada.
CREATE UNIQUE INDEX uq_transfers_idempotency_key
    ON transfers (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
