-- E-mail ownership proof. Everyone who already has an account counts as verified (DEFAULT 1); new sign-ups start unverified
-- (set in code). Only the SHA-256 of a verification token is stored, like password reset tokens.
ALTER TABLE users ADD COLUMN email_verified bit(1) NOT NULL DEFAULT 1;

CREATE TABLE email_verification_tokens (
  id bigint NOT NULL AUTO_INCREMENT,
  token_hash varchar(64) NOT NULL,
  user_id bigint NOT NULL,
  expiry_date datetime(6) NOT NULL,
  used bit(1) NOT NULL DEFAULT 0,
  created_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_email_verification_token_hash (token_hash),
  KEY idx_email_verification_user (user_id),
  CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
