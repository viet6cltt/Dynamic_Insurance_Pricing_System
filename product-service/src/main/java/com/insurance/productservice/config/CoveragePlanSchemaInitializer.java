package com.insurance.productservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoveragePlanSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try (java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equalsIgnoreCase(dbName)) {
                log.info("Database product is {}, skipping PostgreSQL compatibility check", dbName);
                return;
            }
        } catch (Exception e) {
            log.warn("Failed to check database product name, executing script anyway", e);
        }

        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = 'coverage_plans'
                    ) THEN
                        ALTER TABLE coverage_plans
                            ADD COLUMN IF NOT EXISTS loading_rate NUMERIC(5, 4);

                        UPDATE coverage_plans
                        SET loading_rate = 0.2000
                        WHERE loading_rate IS NULL;

                        ALTER TABLE coverage_plans
                            ADD COLUMN IF NOT EXISTS reimbursement_enabled BOOLEAN NOT NULL DEFAULT FALSE;

                        IF NOT EXISTS (
                            SELECT 1
                            FROM pg_constraint
                            WHERE conname = 'coverage_plans_loading_rate_range'
                        ) THEN
                            ALTER TABLE coverage_plans
                                ADD CONSTRAINT coverage_plans_loading_rate_range
                                CHECK (loading_rate >= 0 AND loading_rate <= 1);
                        END IF;

                        ALTER TABLE coverage_plans
                            ALTER COLUMN loading_rate SET NOT NULL;

                        ALTER TABLE coverage_plans
                            DROP COLUMN IF EXISTS base_premium;
                    END IF;
                END $$;
                """);

        log.info("Coverage plan schema compatibility check completed");
    }
}
