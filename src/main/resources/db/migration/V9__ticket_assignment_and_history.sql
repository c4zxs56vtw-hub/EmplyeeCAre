-- ==========================================
-- EMPLOYEECARE
-- V9 : Attribution et historique des tickets
-- ==========================================

-- 1. Ajouter le responsable du signalement

ALTER TABLE tickets

ADD COLUMN assigned_responsable_id UUID,

ADD COLUMN taken_at TIMESTAMPTZ,

ADD CONSTRAINT fk_ticket_assigned_responsable
    FOREIGN KEY (assigned_responsable_id)
    REFERENCES app_users(id);


-- 2. Index pour les signalements attribues

CREATE INDEX idx_tickets_assigned_responsable
ON tickets(assigned_responsable_id);


-- 3. Table historique des actions

CREATE TABLE ticket_events (

    id UUID PRIMARY KEY,

    ticket_id UUID NOT NULL,

    actor_id UUID NOT NULL,

    event_type VARCHAR(50) NOT NULL,

    old_status VARCHAR(30),

    new_status VARCHAR(30),

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ticket_event_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id),

    CONSTRAINT fk_ticket_event_actor
        FOREIGN KEY (actor_id)
        REFERENCES app_users(id)
);


-- 4. Index pour consulter l'historique

CREATE INDEX idx_ticket_events_history
ON ticket_events(ticket_id, created_at DESC);
