CREATE TABLE revoked_access_tokens (

                                       jti UUID PRIMARY KEY,

                                       user_id UUID NOT NULL,

                                       expires_at TIMESTAMPTZ NOT NULL,

                                       revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       CONSTRAINT fk_revoked_access_tokens_user
                                           FOREIGN KEY (user_id)
                                               REFERENCES app_users(id)
                                               ON DELETE CASCADE
);

CREATE INDEX idx_revoked_access_tokens_expires_at
    ON revoked_access_tokens(expires_at);