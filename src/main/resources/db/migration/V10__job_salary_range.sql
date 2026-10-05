-- Salary becomes a range: `salary` stays as the minimum, `salary_max` is the maximum (NULL = not given).
-- Existing postings had one fixed amount, so their maximum equals it; "Thoa thuan" (0 and no maximum) stays as is.
ALTER TABLE jobs ADD COLUMN salary_max double NULL;
UPDATE jobs SET salary_max = salary WHERE salary > 0;
