-- ============================================
-- EMPLOYEECARE
-- V11 : Gestion des pièces jointes
-- ============================================

CREATE TABLE ticket_attachments (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL,
    uploaded_by UUID NOT NULL,
    original_filename VARCHAR(180) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attachment_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets(id),

    CONSTRAINT fk_attachment_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES app_users(id),

    CONSTRAINT chk_attachment_size
        CHECK (
            size_bytes > 0
            AND size_bytes <= 5242880
        )
);

CREATE INDEX idx_ticket_attachments_ticket
    ON ticket_attachments(ticket_id, created_at);
