package com.insurance.pricingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PremiumQuoteSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            log.info("Skipping premium quote schema compatibility check for non-PostgreSQL database");
            return;
        }

        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = 'premium_quotes'
                    ) THEN
                        ALTER TABLE premium_quotes
                            ADD COLUMN IF NOT EXISTS predicted_frequency NUMERIC(15, 6);

                        ALTER TABLE premium_quotes
                            ADD COLUMN IF NOT EXISTS predicted_severity NUMERIC(15, 2);

                        ALTER TABLE premium_quotes
                            ADD COLUMN IF NOT EXISTS pure_premium NUMERIC(15, 2);

                        ALTER TABLE premium_quotes
                            ADD COLUMN IF NOT EXISTS loading_rate NUMERIC(5, 4);

                        ALTER TABLE premium_quotes
                            ADD COLUMN IF NOT EXISTS reimbursement VARCHAR(255);

                        UPDATE premium_quotes
                        SET predicted_frequency = COALESCE(predicted_frequency, 0),
                            loading_rate = COALESCE(loading_rate, 0),
                            reimbursement = COALESCE(reimbursement, 'No');

                        ALTER TABLE premium_quotes
                            ALTER COLUMN reimbursement SET NOT NULL;

                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'premium_quotes'
                              AND column_name = 'predicted_annual_claim_cost'
                        ) THEN
                            UPDATE premium_quotes
                            SET predicted_severity = COALESCE(predicted_severity, predicted_annual_claim_cost, 0);
                        ELSE
                            UPDATE premium_quotes
                            SET predicted_severity = COALESCE(predicted_severity, 0);
                        END IF;

                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'premium_quotes'
                              AND column_name = 'base_premium'
                        ) THEN
                            UPDATE premium_quotes
                            SET pure_premium = COALESCE(pure_premium, base_premium, final_premium, 0);
                        ELSE
                            UPDATE premium_quotes
                            SET pure_premium = COALESCE(pure_premium, final_premium, 0);
                        END IF;

                        ALTER TABLE premium_quotes
                            ALTER COLUMN predicted_frequency SET NOT NULL;

                        ALTER TABLE premium_quotes
                            ALTER COLUMN predicted_severity SET NOT NULL;

                        ALTER TABLE premium_quotes
                            ALTER COLUMN pure_premium SET NOT NULL;

                        ALTER TABLE premium_quotes
                            ALTER COLUMN loading_rate SET NOT NULL;
                    END IF;
                END $$;
                """);

        log.info("Premium quote schema compatibility check completed");
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")));
    }
}
