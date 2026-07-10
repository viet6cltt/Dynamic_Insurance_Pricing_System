ALTER TABLE premium_quotes
    ADD COLUMN IF NOT EXISTS reimbursement VARCHAR(255);

UPDATE premium_quotes
SET reimbursement = COALESCE(reimbursement, 'No');

ALTER TABLE premium_quotes
    ALTER COLUMN reimbursement SET NOT NULL;
