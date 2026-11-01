-- What an employer gives to prove the company is real: tax code (MST), a contact phone, and the business licence.
-- NULL for companies that existed before (they stay approved).
ALTER TABLE companies
    ADD COLUMN tax_code varchar(14) NULL,
    ADD COLUMN phone varchar(20) NULL,
    ADD COLUMN license_file varchar(255) NULL,
    ADD UNIQUE KEY uk_companies_tax_code (tax_code);
