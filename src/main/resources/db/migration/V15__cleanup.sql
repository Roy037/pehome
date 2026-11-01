-- Housekeeping: drop the two unused roles of the original seed, make e-mail unique, and stop keeping refresh tokens in clear text.
DELETE pr FROM permission_role pr JOIN roles r ON r.id = pr.role_id
    WHERE r.name IN ('SALER', 'Upper Management') AND NOT EXISTS (SELECT 1 FROM users u WHERE u.role_id = r.id);
DELETE r FROM roles r
    WHERE r.name IN ('SALER', 'Upper Management') AND NOT EXISTS (SELECT 1 FROM users u WHERE u.role_id = r.id);

-- fails on purpose if two accounts already share an e-mail: merge or rename them first
ALTER TABLE users DROP INDEX idx_users_email, ADD UNIQUE INDEX uk_users_email (email);

-- refresh_token now holds a SHA-256 of the token; clear-text tokens from before would never match, so remove them (everyone signs in again once)
UPDATE users SET refresh_token = NULL;
