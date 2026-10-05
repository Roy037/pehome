-- A job's deadline used to be stored as midnight at the START of the chosen day, so postings closed one day early
-- (on the very day shown as "Hạn nộp hồ sơ"). Move those deadlines to the last second of that day.
-- Applies only to rows that sit exactly on a midnight, in UTC or in Vietnam time (UTC+7).
UPDATE jobs
SET end_date = end_date + INTERVAL 1 DAY - INTERVAL 1 SECOND
WHERE end_date IS NOT NULL
  AND (TIME_TO_SEC(TIME(end_date)) = 0
       OR TIME_TO_SEC(TIME(CONVERT_TZ(end_date, '+00:00', '+07:00'))) = 0);
