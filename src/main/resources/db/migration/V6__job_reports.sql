-- Candidates can report a suspicious or wrong job post; one report per user and job. Admins read them.
CREATE TABLE job_reports (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) DEFAULT NULL,
  detail varchar(1000) DEFAULT NULL,
  reason enum('SCAM','MISLEADING','DUPLICATE','EXPIRED','INAPPROPRIATE','OTHER') NOT NULL,
  job_id bigint NOT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_reports_user_job (user_id, job_id),
  KEY idx_job_reports_job (job_id),
  CONSTRAINT fk_job_reports_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_job_reports_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
