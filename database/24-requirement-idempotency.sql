BEGIN;

ALTER TABLE customer_requirement
  ADD COLUMN IF NOT EXISTS submission_key VARCHAR(80),
  ADD COLUMN IF NOT EXISTS submission_hash CHAR(64);

ALTER TABLE customer_requirement
  DROP CONSTRAINT IF EXISTS ck_customer_requirement_submission_pair;

ALTER TABLE customer_requirement
  ADD CONSTRAINT ck_customer_requirement_submission_pair
    CHECK ((submission_key IS NULL) = (submission_hash IS NULL));

DROP INDEX IF EXISTS uq_customer_requirement_submission_key;

CREATE UNIQUE INDEX uq_customer_requirement_submission_key
  ON customer_requirement(customer_id, organization_id, submission_key)
  WHERE submission_key IS NOT NULL;

COMMIT;
