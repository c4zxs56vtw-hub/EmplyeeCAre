-- ============================================
-- EMPLOYEECARE
-- V8 : Gestion des signalements
-- ============================================

-- Sequence pour generer des references uniques.

CREATE SEQUENCE ticket_reference_seq
    START WITH 1
    INCREMENT BY 1;


-- Table principale des signalements.

CREATE TABLE tickets (

    id UUID PRIMARY KEY,

    reference VARCHAR(40) NOT NULL UNIQUE,

    title VARCHAR(200) NOT NULL,

    description TEXT NOT NULL,

    category_code VARCHAR(100) NOT NULL,

    employee_id UUID NOT NULL,

    department_id UUID NOT NULL,

    priority VARCHAR(20) NOT NULL DEFAULT 'MOYENNE',

    status VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE',

    is_confidential BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,


    -- Relations

    CONSTRAINT fk_ticket_employee
        FOREIGN KEY (employee_id)
        REFERENCES app_users(id),

    CONSTRAINT fk_ticket_department
        FOREIGN KEY (department_id)
        REFERENCES departments(id),

    CONSTRAINT fk_ticket_category
        FOREIGN KEY (category_code)
        REFERENCES ticket_categories(code),


    -- Priorites autorisees

    CONSTRAINT chk_ticket_priority
        CHECK (
            priority IN (
                'FAIBLE',
                'MOYENNE',
                'HAUTE',
                'CRITIQUE'
            )
        ),


    -- Statuts autorises

    CONSTRAINT chk_ticket_status
        CHECK (
            status IN (
                'EN_ATTENTE',
                'DIAGNOSTIC_EN_COURS',
                'ATTENTE_VALIDATION',
                'RESOLU',
                'CLOTURE',
                'ROUVERT'
            )
        )
);


-- Index pour rechercher les signalements d'un employe.

CREATE INDEX idx_tickets_employee
    ON tickets(employee_id, created_at DESC);


-- Index pour les signalements d'un departement.

CREATE INDEX idx_tickets_department
    ON tickets(department_id, created_at DESC);


-- Index pour les categories et les statuts.

CREATE INDEX idx_tickets_category_status
    ON tickets(category_code, status);
