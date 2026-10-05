-- Admin can lock an account: it cannot sign in and its live session ends. The e-mail index serves the per-request lock check.
ALTER TABLE users ADD COLUMN locked bit(1) NOT NULL DEFAULT 0;
CREATE INDEX idx_users_email ON users (email);
