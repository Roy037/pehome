-- Premium plan purchases (one-time 30-day pass per order, paid through VNPay).
CREATE TABLE orders (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint NOT NULL,
  plan enum('BASIC','STANDARD','PREMIUM') NOT NULL,
  amount bigint NOT NULL,
  status enum('PENDING','PAID','FAILED') NOT NULL,
  txn_ref varchar(40) NOT NULL,
  gateway_txn_no varchar(40) DEFAULT NULL,
  bank_code varchar(20) DEFAULT NULL,
  response_code varchar(5) DEFAULT NULL,
  created_at datetime(6) DEFAULT NULL,
  paid_at datetime(6) DEFAULT NULL,
  starts_at datetime(6) DEFAULT NULL,
  ends_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_txn_ref (txn_ref),
  KEY idx_orders_user (user_id, status, ends_at),
  -- no cascade: payment records are never removed together with the user
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
