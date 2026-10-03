--liquibase formatted sql

--changeset ai-commerce-support:003-constrain-shipment-carrier
ALTER TABLE shipments ADD CONSTRAINT ck_shipments_carrier
    CHECK (carrier IN ('GHTK', 'GHN', 'VIETTEL_POST'));

--rollback ALTER TABLE shipments DROP CONSTRAINT ck_shipments_carrier;
