-- Employers can buy things too: a job pinned to the top of the list, extra places for open jobs, and the talent
-- directory. They share the orders table with the candidate plans: a plan order has `plan` and no `product`, an
-- employer order has `product`, the company that bought it, and (for a pin) the job.
ALTER TABLE orders MODIFY plan enum('BASIC','STANDARD','PREMIUM') NULL;

ALTER TABLE orders
    ADD COLUMN product enum('JOB_PIN_7','JOB_PIN_30','JOB_SLOTS_5','TALENT_30') NULL,
    ADD COLUMN company_id bigint NULL,
    ADD COLUMN job_id bigint NULL,
    ADD KEY idx_orders_company (company_id, product, status, ends_at),
    ADD CONSTRAINT fk_orders_company FOREIGN KEY (company_id) REFERENCES companies (id),
    ADD CONSTRAINT fk_orders_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE SET NULL;

-- a pinned job sorts first until this moment
ALTER TABLE jobs
    ADD COLUMN pinned_until datetime(6) NULL,
    ADD KEY idx_jobs_pinned (pinned_until);
