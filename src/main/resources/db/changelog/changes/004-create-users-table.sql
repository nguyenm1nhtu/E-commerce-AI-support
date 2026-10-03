--liquibase formatted sql

--changeset ai-commerce-support:004-create-users-table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email CHECK (LENGTH(TRIM(email)) > 0),
    CONSTRAINT ck_users_password_hash CHECK (LENGTH(TRIM(password_hash)) > 0),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'SUPPORT_AGENT'))
);

--rollback DROP TABLE users;
