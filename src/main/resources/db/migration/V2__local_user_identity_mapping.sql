ALTER TABLE users ADD COLUMN supabase_user_id UUID;
ALTER TABLE users ADD COLUMN registration_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';
UPDATE users SET supabase_user_id = id;
ALTER TABLE users ADD CONSTRAINT uq_users_supabase_user_id UNIQUE (supabase_user_id);
ALTER TABLE users ADD CONSTRAINT ck_users_registration_status
    CHECK (registration_status IN ('PENDING', 'COMPLETED', 'FAILED'));
ALTER TABLE users ADD CONSTRAINT ck_users_completed_identity
    CHECK ((registration_status = 'COMPLETED' AND supabase_user_id IS NOT NULL)
        OR (registration_status <> 'COMPLETED' AND supabase_user_id IS NULL AND status = 'INACTIVE'));