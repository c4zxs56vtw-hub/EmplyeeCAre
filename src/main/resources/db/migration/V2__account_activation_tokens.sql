-- =============================================
-- EMPLOYEECARE
-- V2 : Gestion des jetons d'activation
-- =============================================

CREATE TABLE account_activation_tokens (

                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                           user_id UUID NOT NULL,

                                           token_hash VARCHAR(64) NOT NULL UNIQUE,

                                           expires_at TIMESTAMPTZ NOT NULL,

                                           used_at TIMESTAMPTZ,

                                           revoked_at TIMESTAMPTZ,

                                           created_at TIMESTAMPTZ NOT NULL
                                                               DEFAULT CURRENT_TIMESTAMP,

                                           CONSTRAINT fk_activation_token_user
                                               FOREIGN KEY (user_id)
                                                   REFERENCES app_users(id)
                                                   ON DELETE CASCADE
);

CREATE INDEX idx_activation_token_user
    ON account_activation_tokens(user_id);