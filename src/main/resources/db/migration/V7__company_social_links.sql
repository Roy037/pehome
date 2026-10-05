-- Social profiles shown on the job and company pages.
ALTER TABLE companies
    ADD COLUMN facebook_url varchar(255) NULL,
    ADD COLUMN linkedin_url varchar(255) NULL,
    ADD COLUMN twitter_url varchar(255) NULL,
    ADD COLUMN pinterest_url varchar(255) NULL,
    ADD COLUMN instagram_url varchar(255) NULL,
    ADD COLUMN youtube_url varchar(255) NULL;
