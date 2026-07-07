ALTER TABLE coverage_plans
    ADD COLUMN IF NOT EXISTS loading_rate NUMERIC(5, 4);

UPDATE coverage_plans
SET loading_rate = 0.2000
WHERE loading_rate IS NULL;

ALTER TABLE coverage_plans
    ALTER COLUMN loading_rate SET NOT NULL;

ALTER TABLE coverage_plans
    ADD CONSTRAINT coverage_plans_loading_rate_range
    CHECK (loading_rate >= 0 AND loading_rate <= 1);

ALTER TABLE coverage_plans
    DROP COLUMN IF EXISTS base_premium;
