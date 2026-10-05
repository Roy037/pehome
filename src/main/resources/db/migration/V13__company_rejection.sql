-- Employer verification: an admin can reject a company with a reason. Rejected = not approved + a reason; pending = not approved, no reason.
ALTER TABLE companies
    ADD COLUMN rejection_reason varchar(500) NULL,
    ADD COLUMN rejected_at datetime(6) NULL;
