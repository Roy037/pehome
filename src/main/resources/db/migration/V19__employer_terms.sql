-- Which version of the employer terms of service the account accepted, and when. NULL = never (accounts that
-- existed before the terms were introduced are asked at their next sign-in).
ALTER TABLE users
    ADD COLUMN terms_version varchar(20) NULL,
    ADD COLUMN terms_accepted_at datetime(6) NULL;
