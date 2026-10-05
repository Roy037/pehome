-- Social sign-in: which Google / Facebook / LinkedIn account belongs to which user. (provider, provider_user_id) is unique,
-- so one external account can only ever open one itjobs account.
CREATE TABLE user_identities (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint NOT NULL,
  provider enum('GOOGLE','FACEBOOK','LINKEDIN') NOT NULL,
  provider_user_id varchar(191) NOT NULL,
  created_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_identity_provider_user (provider, provider_user_id),
  KEY idx_identity_user (user_id),
  CONSTRAINT fk_identity_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
