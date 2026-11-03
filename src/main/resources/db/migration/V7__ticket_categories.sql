-- =====================================
-- EMPLOYEECARE
-- V7 : Catégories de signalements
-- =====================================

CREATE TABLE ticket_categories (

    code VARCHAR(100) PRIMARY KEY,

    label VARCHAR(150) NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Catégories initiales

INSERT INTO ticket_categories (
    code,
    label,
    active
)
VALUES

('TECHNIQUE', 'Problèmes techniques', TRUE),

('ADMINISTRATIF', 'Problèmes administratifs', TRUE),

('RH', 'Ressources humaines', TRUE),

('PLAINTES', 'Plaintes et réclamations', TRUE),

('CONFLITS', 'Conflits professionnels', TRUE),

('CONDITIONS_TRAVAIL', 'Conditions de travail', TRUE),

('SUGGESTIONS', 'Suggestions d''amélioration', TRUE)

ON CONFLICT (code) DO NOTHING;
