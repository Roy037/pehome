-- A candidate can keep up to three files on the profile: an English and a Vietnamese CV, certificates such as IELTS
-- or SAT. The single cv_* columns of candidate_profiles are copied over as the first file. They stay in place for
-- now so the previous build still starts against this schema; drop them in a later migration.
CREATE TABLE profile_cvs (
  sort_order int NOT NULL,
  profile_id bigint NOT NULL,
  url varchar(255) NOT NULL,
  name varchar(255) DEFAULT NULL,
  updated_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (profile_id, sort_order),
  CONSTRAINT fk_profile_cvs FOREIGN KEY (profile_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO profile_cvs (profile_id, sort_order, url, name, updated_at)
SELECT id, 0, cv_url, cv_name, cv_updated_at FROM candidate_profiles WHERE cv_url IS NOT NULL AND cv_url <> '';
