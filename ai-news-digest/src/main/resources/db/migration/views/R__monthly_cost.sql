-- Monthly AI costs per calendar month in Europe/Berlin (docs/03-datenmodell.md).
-- Sum of research (provider_result) and aggregation (aggregation_result) costs; a run is assigned
-- to the month of its start time.
CREATE OR REPLACE VIEW monthly_cost AS
SELECT date_trunc('month', r.started_at AT TIME ZONE 'Europe/Berlin')::date AS month,
       sum(c.cost_usd)                                                     AS cost_usd
FROM digest_run r
         JOIN (SELECT digest_run_id, cost_usd
               FROM provider_result
               WHERE cost_usd IS NOT NULL
               UNION ALL
               SELECT digest_run_id, cost_usd
               FROM aggregation_result) c ON c.digest_run_id = r.id
GROUP BY 1;
-- The app user gets SELECT through the default privileges from V1 (they also apply to views).
