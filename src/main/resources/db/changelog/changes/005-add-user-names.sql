--liquibase formatted sql

--changeset ai-commerce-support:005-add-user-names
-- Existing users retain NULL names until their profiles are completed.
ALTER TABLE users ADD COLUMN first_name VARCHAR(100);
ALTER TABLE users ADD COLUMN last_name VARCHAR(100);
ALTER TABLE users ADD CONSTRAINT ck_users_first_name CHECK (LENGTH(TRIM(first_name)) > 0);
ALTER TABLE users ADD CONSTRAINT ck_users_last_name CHECK (LENGTH(TRIM(last_name)) > 0);

--rollback ALTER TABLE users DROP CONSTRAINT ck_users_last_name;
--rollback ALTER TABLE users DROP CONSTRAINT ck_users_first_name;
--rollback ALTER TABLE users DROP COLUMN last_name;
--rollback ALTER TABLE users DROP COLUMN first_name;
