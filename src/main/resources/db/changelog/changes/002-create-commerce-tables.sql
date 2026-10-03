--liquibase formatted sql

--changeset ai-commerce-support:002-create-commerce-tables
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    ordered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    CONSTRAINT ck_orders_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_orders_user_id ON orders (user_id);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_name CHECK (LENGTH(TRIM(product_name)) > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_ref VARCHAR(255),
    paid_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_payments_order UNIQUE (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED'))
);

CREATE TABLE shipments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    carrier VARCHAR(255),
    tracking_code VARCHAR(255),
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_shipments_order UNIQUE (order_id),
    CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_shipments_status CHECK (status IN ('PENDING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'RETURNED'))
);

--rollback DROP TABLE shipments;
--rollback DROP TABLE payments;
--rollback DROP TABLE order_items;
--rollback DROP TABLE orders;
