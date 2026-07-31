ALTER TABLE notifications.notifications ADD COLUMN event_id UUID;

-- Índice único parcial: permite múltiples NULL (por si en el futuro se registran
-- notificaciones fuera del flujo de eventos) pero exige unicidad cuando viene informado,
-- que es la base del chequeo de idempotencia del consumidor Kafka.
CREATE UNIQUE INDEX uq_notifications_event_id
    ON notifications.notifications (event_id)
    WHERE event_id IS NOT NULL;
