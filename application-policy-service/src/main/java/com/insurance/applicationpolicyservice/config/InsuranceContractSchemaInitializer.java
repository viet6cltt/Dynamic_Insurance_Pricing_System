package com.insurance.applicationpolicyservice.config;

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
public class InsuranceContractSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            log.info("Skipping insurance contract schema compatibility check for non-PostgreSQL database");
            return;
        }

        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = 'insurance_contracts'
	                    ) THEN
	                        ALTER TABLE insurance_contracts
	                            ADD COLUMN IF NOT EXISTS pure_premium NUMERIC(38, 2);

	                        ALTER TABLE insurance_contracts
	                            ADD COLUMN IF NOT EXISTS loading_rate NUMERIC(10, 4);

	                        IF EXISTS (
	                            SELECT 1
	                            FROM information_schema.columns
	                            WHERE table_schema = 'public'
	                              AND table_name = 'insurance_contracts'
	                              AND column_name = 'base_premium'
	                        ) THEN
	                            UPDATE insurance_contracts
	                            SET pure_premium = COALESCE(pure_premium, base_premium, quoted_premium, 0),
	                                loading_rate = COALESCE(loading_rate, 0);

	                            ALTER TABLE insurance_contracts
	                                DROP COLUMN IF EXISTS base_premium;
	                        ELSE
	                            UPDATE insurance_contracts
	                            SET pure_premium = COALESCE(pure_premium, quoted_premium, 0),
	                                loading_rate = COALESCE(loading_rate, 0);
	                        END IF;

	                        ALTER TABLE insurance_contracts
	                            ALTER COLUMN pure_premium SET NOT NULL;

                        ALTER TABLE insurance_contracts
                            ALTER COLUMN loading_rate SET NOT NULL;
                    END IF;
                END $$;
                """);

        log.info("Insurance contract schema compatibility check completed");
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")));
    }
}
