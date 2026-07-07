ALTER TABLE premium_quotes
    ADD COLUMN IF NOT EXISTS predicted_frequency NUMERIC(15, 6),
    ADD COLUMN IF NOT EXISTS predicted_severity NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS pure_premium NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS loading_rate NUMERIC(5, 4),
    ADD COLUMN IF NOT EXISTS frequency_model_version VARCHAR(255),
    ADD COLUMN IF NOT EXISTS severity_model_version VARCHAR(255);

UPDATE premium_quotes
SET predicted_frequency = COALESCE(predicted_frequency, 0),
    predicted_severity = COALESCE(predicted_severity, 0),
    pure_premium = COALESCE(pure_premium, final_premium, 0),
    loading_rate = COALESCE(loading_rate, 0);

ALTER TABLE premium_quotes
    ALTER COLUMN predicted_frequency SET NOT NULL,
    ALTER COLUMN predicted_severity SET NOT NULL,
    ALTER COLUMN pure_premium SET NOT NULL,
    ALTER COLUMN loading_rate SET NOT NULL;

ALTER TABLE premium_quotes
    ADD CONSTRAINT premium_quotes_frequency_non_negative CHECK (predicted_frequency >= 0),
    ADD CONSTRAINT premium_quotes_severity_non_negative CHECK (predicted_severity >= 0),
    ADD CONSTRAINT premium_quotes_pure_premium_non_negative CHECK (pure_premium >= 0),
    ADD CONSTRAINT premium_quotes_loading_rate_range CHECK (loading_rate >= 0 AND loading_rate <= 1);

ALTER TABLE premium_quotes
    DROP COLUMN IF EXISTS base_premium,
    DROP COLUMN IF EXISTS predicted_annual_claim_cost,
    DROP COLUMN IF EXISTS predicted_health_cost,
    DROP COLUMN IF EXISTS baseline_health_cost,
    DROP COLUMN IF EXISTS raw_portfolio_risk_factor,
    DROP COLUMN IF EXISTS portfolio_risk_factor,
    DROP COLUMN IF EXISTS raw_health_risk_factor,
    DROP COLUMN IF EXISTS health_risk_factor,
    DROP COLUMN IF EXISTS underwriting_adjustment_factor,
    DROP COLUMN IF EXISTS business_adjustment_factor,
    DROP COLUMN IF EXISTS portfolio_model_version,
    DROP COLUMN IF EXISTS health_model_version,
    DROP COLUMN IF EXISTS pricing_rule_version;
