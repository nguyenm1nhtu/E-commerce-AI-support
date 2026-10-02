--liquibase formatted sql

--changeset ai-commerce-support:001-enable-pgvector dbms:postgresql
CREATE EXTENSION IF NOT EXISTS vector;

--rollback DROP EXTENSION IF EXISTS vector;
