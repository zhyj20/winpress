BEGIN;

-- Financial facts must not be duplicated when an administrator retries after a timeout.
-- Historical transactions remain unchanged and keep NULL request identifiers.
ALTER TABLE settlement_transaction
  ADD COLUMN IF NOT EXISTS submission_key VARCHAR(80),
  ADD COLUMN IF NOT EXISTS submission_hash VARCHAR(64);

ALTER TABLE settlement_transaction
  DROP CONSTRAINT IF EXISTS ck_settlement_transaction_submission_pair;

ALTER TABLE settlement_transaction
  ADD CONSTRAINT ck_settlement_transaction_submission_pair
    CHECK ((submission_key IS NULL) = (submission_hash IS NULL));

DROP INDEX IF EXISTS uq_settlement_transaction_submission_key;

CREATE UNIQUE INDEX uq_settlement_transaction_submission_key
  ON settlement_transaction(settlement_order_id, submission_key)
  WHERE submission_key IS NOT NULL;

COMMIT;
