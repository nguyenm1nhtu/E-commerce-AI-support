--liquibase formatted sql

--changeset ai-commerce-support:006-create-ticketing-tables
CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    assigned_to UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tickets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_tickets_assignee FOREIGN KEY (assigned_to) REFERENCES users (id),
    CONSTRAINT ck_tickets_category CHECK (LENGTH(TRIM(category)) > 0),
    CONSTRAINT ck_tickets_status CHECK (status IN ('OPEN', 'AI_HANDLING', 'WAITING_CUSTOMER', 'ESCALATED', 'HUMAN_HANDLING', 'RESOLVED')),
    CONSTRAINT ck_tickets_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
);

CREATE INDEX idx_tickets_user_created ON tickets (user_id, created_at, id);
CREATE INDEX idx_tickets_assignee_status ON tickets (assigned_to, status);
CREATE INDEX idx_tickets_status_created ON tickets (status, created_at, id);

CREATE TABLE ticket_messages (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL,
    sender_type VARCHAR(32) NOT NULL,
    content VARCHAR(10000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ticket_messages_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT ck_ticket_messages_sender CHECK (sender_type IN ('CUSTOMER', 'SUPPORT_AGENT', 'AI', 'SYSTEM')),
    CONSTRAINT ck_ticket_messages_content CHECK (LENGTH(TRIM(content)) > 0)
);

CREATE INDEX idx_ticket_messages_ticket_created ON ticket_messages (ticket_id, created_at, id);

--rollback DROP TABLE ticket_messages;
--rollback DROP TABLE tickets;
