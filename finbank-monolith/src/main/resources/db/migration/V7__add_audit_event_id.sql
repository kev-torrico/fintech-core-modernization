ALTER TABLE audit.audit_entries ADD COLUMN event_id UUID;

-- Índice único parcial: permite múltiples NULL (auditoría generada localmente, sin
-- pasar por Kafka, como el TransferUseCase legado) pero exige unicidad cuando el
-- event_id viene informado, que es la base del chequeo de idempotencia del consumidor.
CREATE UNIQUE INDEX uq_audit_entries_event_id
    ON audit.audit_entries (event_id)
    WHERE event_id IS NOT NULL;
