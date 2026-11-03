-- ============================================
-- EMPLOYEECARE
-- V6 : Gestion des comptes responsables
-- ============================================

CREATE TABLE responsable_accounts (

    user_id UUID PRIMARY KEY,

    department_id UUID NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    is_confidential_hab BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_responsable_user
        FOREIGN KEY (user_id)
        REFERENCES app_users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_responsable_department
        FOREIGN KEY (department_id)
        REFERENCES departments(id)
);

CREATE TABLE responsable_assigned_categories (

    responsable_user_id UUID NOT NULL,

    category_code VARCHAR(100) NOT NULL,

    PRIMARY KEY (
        responsable_user_id,
        category_code
    ),

    CONSTRAINT fk_responsable_assigned_categories
        FOREIGN KEY (responsable_user_id)
        REFERENCES responsable_accounts(user_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_responsable_department
    ON responsable_accounts(department_id);
