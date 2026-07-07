ALTER TABLE insurance_contracts
    ADD COLUMN IF NOT EXISTS pure_premium NUMERIC,
    ADD COLUMN IF NOT EXISTS loading_rate NUMERIC;

UPDATE insurance_contracts
SET pure_premium = COALESCE(pure_premium, base_premium, quoted_premium, 0),
    loading_rate = COALESCE(loading_rate, 0);

ALTER TABLE insurance_contracts
    ALTER COLUMN pure_premium SET NOT NULL,
    ALTER COLUMN loading_rate SET NOT NULL;

ALTER TABLE insurance_contracts
    ADD CONSTRAINT insurance_contracts_loading_rate_non_negative CHECK (loading_rate >= 0);

ALTER TABLE insurance_contracts
    DROP COLUMN IF EXISTS base_premium;
