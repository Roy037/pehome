-- How the customer chose to pay (null on orders made before the choice existed).
ALTER TABLE orders ADD COLUMN method enum('MOMO','VNPAY','VIETQR','ZALOPAY','BANK','CARD') NULL;
