-- Public company profile: banner, website, Google Maps embed URL and product/outsource type.
ALTER TABLE companies
    ADD COLUMN banner varchar(255) NULL,
    ADD COLUMN website varchar(255) NULL,
    ADD COLUMN map_embed_url varchar(1000) NULL,
    ADD COLUMN company_type enum('PRODUCT','OUTSOURCE') NULL;
