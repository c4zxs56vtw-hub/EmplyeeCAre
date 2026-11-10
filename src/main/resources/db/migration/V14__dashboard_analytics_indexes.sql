-- ============================================
-- EMPLOYEECARE
-- V14 : Optimisation des statistiques
-- ============================================

-- 1. Index sur les dates des tickets non sensibles.
CREATE INDEX IF NOT EXISTS idx_tickets_dashboard_created_at
    ON tickets (created_at)
    WHERE is_anonymous = FALSE
      AND is_confidential = FALSE;

-- 2. Index pour les statistiques des responsables.
CREATE INDEX IF NOT EXISTS idx_tickets_dashboard_scope
    ON tickets (
        department_id,
        category_code,
        created_at
    )
    WHERE is_anonymous = FALSE
      AND is_confidential = FALSE;

-- 3. Index pour les événements de traitement.
CREATE INDEX IF NOT EXISTS idx_ticket_events_dashboard
    ON ticket_events (
        ticket_id,
        event_type,
        created_at
    );
