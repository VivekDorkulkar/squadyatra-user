CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    date_of_birth DATE,
    city VARCHAR(80),
    country_code VARCHAR(2),
    time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    locale VARCHAR(35) NOT NULL DEFAULT 'en',
    bio VARCHAR(500),
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_consent BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_consent_updated_at TIMESTAMP(6) WITH TIME ZONE,
    user_type VARCHAR(20) NOT NULL DEFAULT 'CONSUMER' CHECK (user_type IN ('CONSUMER', 'AGENCY', 'ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    status_reason VARCHAR(300),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(trim(email)))
);
CREATE INDEX ix_users_status_type ON users(status, user_type, created_at, id);

CREATE TABLE user_audit (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    user_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    reason VARCHAR(300),
    correlation_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_user_audit_user ON user_audit(user_id, occurred_at);

CREATE TABLE identity_sync (
    user_id UUID PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    failed BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE identity_sync ADD CONSTRAINT fk_identity_sync_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX ix_identity_sync_due ON identity_sync(failed, next_attempt_at);