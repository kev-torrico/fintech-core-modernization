CREATE SCHEMA IF NOT EXISTS notifications;

-- Tabla principal de notificaciones aislada (Database-per-Service)
CREATE TABLE IF NOT EXISTS notifications.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);

-- Índice para búsquedas rápidas por usuario
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications.notifications(user_id);