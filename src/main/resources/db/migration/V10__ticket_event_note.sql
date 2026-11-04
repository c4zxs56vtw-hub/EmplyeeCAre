-- ======================================
-- EMPLOYEECARE
-- V10 : Notes des événements
-- ======================================

ALTER TABLE ticket_events
ADD COLUMN note TEXT;

ALTER TABLE ticket_events
ADD CONSTRAINT chk_ticket_event_note_length
CHECK (
    note IS NULL
    OR CHAR_LENGTH(note) <= 2000
);
