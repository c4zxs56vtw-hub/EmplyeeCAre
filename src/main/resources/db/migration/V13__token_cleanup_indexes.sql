-- ============================================
-- EMPLOYEECARE
-- V13 : Index pour le nettoyage des jetons
-- ============================================

-- 1. Index sur l'expiration des JWT révoqués
CREATE INDEX IF NOT EXISTS idx_revoked_access_tokens_expires_at
    ON revoked_access_tokens (expires_at);

-- 2. Index sur l'expiration des jetons d'activation
CREATE INDEX IF NOT EXISTS idx_account_activation_tokens_expires_at
    ON account_activation_tokens (expires_at);
