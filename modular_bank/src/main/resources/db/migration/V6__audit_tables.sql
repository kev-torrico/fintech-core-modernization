-- Note: user_id columns reference auth.users but no FK constraint is added (cross-schema module isolation).

CREATE TABLE audit.audit_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_user_id ON audit.audit_entries(user_id);
CREATE INDEX idx_audit_created_at ON audit.audit_entries(created_at);
