ALTER TABLE walk_reservation
    ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_walk_reservation_reminder_due
    ON walk_reservation (scheduled_at)
    WHERE status = 'SCHEDULED'
      AND reminder_sent_at IS NULL;
