--liquibase formatted sql

--changeset ai-commerce-support:007-constrain-ticket-category
ALTER TABLE tickets ADD CONSTRAINT ck_tickets_category_enum
    CHECK (category IN ('ORDER', 'PAYMENT', 'SHIPMENT', 'RETURN', 'REFUND', 'OTHER'));

--rollback ALTER TABLE tickets DROP CONSTRAINT ck_tickets_category_enum;
