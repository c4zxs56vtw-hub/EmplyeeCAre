-- ============================================
-- EMPLOYEECARE
-- V12 : Signalements anonymes
-- ============================================

-- 1. Un signalement anonyme n'a pas d'employé associé.
ALTER TABLE tickets
    ALTER COLUMN employee_id DROP NOT NULL;

-- 2. Identifier explicitement les signalements anonymes.
ALTER TABLE tickets
    ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Assurer la cohérence des données.
ALTER TABLE tickets
    ADD CONSTRAINT chk_ticket_anonymous_author
    CHECK (
        (
            is_anonymous = TRUE
            AND employee_id IS NULL
        )
        OR
        (
            is_anonymous = FALSE
            AND employee_id IS NOT NULL
        )
    );

-- 4. Autoriser un événement sans utilisateur identifié.
ALTER TABLE ticket_events
    ALTER COLUMN actor_id DROP NOT NULL;

-- 5. Enregistrer la nature de l'intervenant.
ALTER TABLE ticket_events
    ADD COLUMN actor_kind VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE ticket_events
    ADD CONSTRAINT chk_ticket_event_actor_kind
    CHECK (
        (
            actor_kind = 'USER'
            AND actor_id IS NOT NULL
        )
        OR
        (
            actor_kind = 'ANONYMOUS'
            AND actor_id IS NULL
        )
    );

-- 6. Enregistrer les empreintes des codes de suivi.
CREATE TABLE anonymous_ticket_access (
    ticket_id UUID PRIMARY KEY,
    access_key_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_anonymous_ticket_access
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE CASCADE
);
