-- Optional cover letter on an application (the CV itself is a PDF upload or a Drive/Dropbox/OneDrive link kept in `url`).
ALTER TABLE resumes ADD COLUMN cover_letter text NULL;
