-- ATS pipeline: SHORTLISTED / INTERVIEW / ACCEPTED statuses (APPROVED becomes ACCEPTED), employer evaluation
-- (score 1-10 + remark), interview schedule and the note sent to the candidate with a decision.

-- 1) widen the enum while APPROVED still exists, 2) convert the rows, 3) drop APPROVED
ALTER TABLE resumes MODIFY status enum('PENDING','REVIEWING','APPROVED','SHORTLISTED','INTERVIEW','ACCEPTED','REJECTED') DEFAULT NULL;
UPDATE resumes SET status = 'ACCEPTED' WHERE status = 'APPROVED';
ALTER TABLE resumes MODIFY status enum('PENDING','REVIEWING','SHORTLISTED','INTERVIEW','ACCEPTED','REJECTED') DEFAULT NULL;

ALTER TABLE resumes
    ADD COLUMN score int NULL,
    ADD COLUMN remark text NULL,
    ADD COLUMN interview_at datetime(6) NULL,
    ADD COLUMN meeting_link varchar(500) NULL,
    ADD COLUMN decision_note text NULL;
