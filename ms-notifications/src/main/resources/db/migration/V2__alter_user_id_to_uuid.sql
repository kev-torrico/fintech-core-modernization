-- 1. Modificacion de la columna user_id de VARCHAR a UUID  
ALTER TABLE notifications.notifications 
    ALTER COLUMN user_id TYPE UUID USING user_id::uuid;

-- 2. Eliminar el índice antiguo sobre VARCHAR 
DROP INDEX IF EXISTS notifications.idx_notifications_user_id;

-- 3. Recrear el índice optimizado para el tipo UUID
CREATE INDEX idx_notifications_user_id ON notifications.notifications(user_id);