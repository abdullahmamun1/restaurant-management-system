-- V2__users.sql  (M1: Authentication & RBAC)
-- Pre-registered application users. There is no self-signup (SRS §2.3, FR-02);
-- rows are seeded by DataInitializer on startup so hashes come from the app's encoder.

CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT app_user_role_check
        CHECK (role IN ('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER'))
);

CREATE INDEX idx_app_user_username ON app_user (username);
