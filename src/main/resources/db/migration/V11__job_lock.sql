-- Moderation: an admin can lock a job post (hidden from the public, no new applications) with a reason.
ALTER TABLE jobs
    ADD COLUMN locked bit(1) NOT NULL DEFAULT 0,
    ADD COLUMN lock_reason varchar(500) NULL,
    ADD COLUMN locked_at datetime(6) NULL;
